/*
 * (C) Copyright 2007-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.storage.sql;

import java.io.Serializable;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.resource.ResourceException;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.Interaction;
import javax.resource.cci.LocalTransaction;
import javax.resource.cci.ResultSetInfo;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.StringUtils;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Type;
import org.nuxeo.ecm.core.storage.Credentials;
import org.nuxeo.ecm.core.storage.StorageException;

/**
 * The session is the main high level access point to data from the underlying
 * database.
 *
 * @author Florent Guillaume
 */
public class SessionImpl implements Session, XAResource {

    private static final Log log = LogFactory.getLog(SessionImpl.class);

    private final RepositoryImpl repository;

    protected final SchemaManager schemaManager;

    private final Mapper mapper;

    private final Credentials credentials;

    private final Model model;

    private final PersistenceContext context;

    private boolean live;

    private boolean inTransaction;

    private Node rootNode;

    SessionImpl(RepositoryImpl repository, SchemaManager schemaManager,
            Mapper mapper, RepositoryImpl.Invalidators invalidators,
            Credentials credentials) throws StorageException {
        this.repository = repository;
        this.schemaManager = schemaManager;
        this.mapper = mapper;
        this.credentials = credentials;
        model = mapper.getModel();
        context = new PersistenceContext(mapper, invalidators);
        live = true;
        inTransaction = false;
        computeRootNode();
    }

    /*
     * ----- javax.resource.cci.Connection -----
     */

    public void close() {
        closeSession();
        repository.closeSession(this);
    }

    protected void closeSession() {
        live = false;
        // this closes the mapper and therefore the connection
        context.close();
    }

    public Interaction createInteraction() throws ResourceException {
        throw new UnsupportedOperationException();
    }

    public LocalTransaction getLocalTransaction() throws ResourceException {
        throw new UnsupportedOperationException();
    }

    public ConnectionMetaData getMetaData() throws ResourceException {
        throw new UnsupportedOperationException();
    }

    public ResultSetInfo getResultSetInfo() throws ResourceException {
        throw new UnsupportedOperationException();
    }

    /*
     * ----- javax.transaction.xa.XAResource -----
     */

    public boolean isSameRM(XAResource xaresource) {
        return xaresource == this;
    }

    public void start(Xid xid, int flags) throws XAException {
        if (flags == TMNOFLAGS) {
            context.processInvalidations();
        }
        mapper.start(xid, flags);
        inTransaction = true;
    }

    public void end(Xid xid, int flags) throws XAException {
        mapper.end(xid, flags);
    }

    public int prepare(Xid xid) throws XAException {
        return mapper.prepare(xid);
    }

    public void commit(Xid xid, boolean onePhase) throws XAException {
        try {
            mapper.commit(xid, onePhase);
        } finally {
            inTransaction = false;
            context.notifyInvalidations();
        }
    }

    public void rollback(Xid xid) throws XAException {
        try {
            mapper.rollback(xid);
        } finally {
            inTransaction = false;
            context.notifyInvalidations();
        }
    }

    public void forget(Xid xid) throws XAException {
        mapper.forget(xid);
    }

    public Xid[] recover(int flag) throws XAException {
        return mapper.recover(flag);
    }

    public boolean setTransactionTimeout(int seconds) throws XAException {
        return mapper.setTransactionTimeout(seconds);
    }

    public int getTransactionTimeout() throws XAException {
        return mapper.getTransactionTimeout();
    }

    /*
     * ----- Session -----
     */

    public boolean isLive() {
        return live;
    }

    public Model getModel() {
        return model;
    }

    public Node getRootNode() {
        checkLive();
        return rootNode;
    }

    public void save() throws StorageException {
        checkLive();
        context.save();
        if (!inTransaction) {
            context.notifyInvalidations();
            // as we don't have a way to know when the next non-transactional
            // statement will start, process invalidations immediately
            context.processInvalidations();
        }
    }

    public Node getNodeById(Serializable id) throws StorageException {
        checkLive();
        if (id == null) {
            throw new IllegalArgumentException("Illegal null id");
        }

        // get main row
        SimpleFragment childMain = (SimpleFragment) context.get(
                model.MAIN_TABLE_NAME, id, false);
        if (childMain == null) {
            // HACK try old id
            id = context.getOldId(id);
            if (id == null) {
                return null;
            }
            childMain = (SimpleFragment) context.get(model.MAIN_TABLE_NAME, id,
                    false);
            if (childMain == null) {
                return null;
            }
        }
        String childTypeName = (String) childMain.get(model.MAIN_PRIMARY_TYPE_KEY);
        DocumentType childType = schemaManager.getDocumentType(childTypeName);

        // find hier row
        SimpleFragment childHier = (SimpleFragment) context.getChildById(id,
                false);

        // TODO get all non-cached fragments at once using join / union
        FragmentsMap childFragments = new FragmentsMap();
        for (String fragmentName : model.getTypeFragments(childTypeName)) {
            Fragment fragment = context.get(fragmentName, id, true);
            childFragments.put(fragmentName, fragment);
        }
        /*
         * for (Schema schema : childType.getSchemas()) { String schemaName =
         * schema.getName(); Fragment fragment = context.get(schemaName, id,
         * true); childFragments.put(schemaName, fragment); }
         */

        FragmentGroup childGroup = new FragmentGroup(childMain, childHier,
                childFragments);

        return new Node(childType, this, context, childGroup);
    }

    public Node getParentNode(Node node) throws StorageException {
        checkLive();
        if (node == null) {
            throw new IllegalArgumentException("Illegal null node");
        }
        Serializable id = node.hierFragment.get(model.HIER_PARENT_KEY);
        if (id == null) {
            // root
            return null;
        }
        return getNodeById(id);
    }

    public String getPath(Node node) throws StorageException {
        checkLive();
        List<String> list = new LinkedList<String>();
        while (node != null) {
            list.add(node.getName());
            node = getParentNode(node);
        }
        if (list.size() == 1) {
            // root, special case
            return "/";
        }
        Collections.reverse(list);
        return StringUtils.join(list, "/");
    }

    /* Does not apply to properties for now (no use case). */
    public Node getNodeByPath(String path, Node node) throws StorageException {
        // TODO optimize this to use a dedicated path-based table
        checkLive();
        if (path == null) {
            throw new IllegalArgumentException("Illegal null path");
        }
        int i;
        if (path.startsWith("/")) {
            node = getRootNode();
            if (path.equals("/")) {
                return node;
            }
            i = 1;
        } else {
            if (node == null) {
                throw new IllegalArgumentException(
                        "Illegal relative path with null node: " + path);
            }
            i = 0;
        }
        String[] names = path.split("/", -1);
        for (; i < names.length; i++) {
            String name = names[i];
            if (name.length() == 0) {
                throw new IllegalArgumentException(
                        "Illegal path with empty component: " + path);
            }
            node = getChildNode(node, name, false);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    public Node addChildNode(Node parent, String name, String typeName,
            boolean complexProp) throws StorageException {
        checkLive();
        if (name == null || name.contains("/") || name.equals(".") ||
                name.equals("..")) {
            // XXX real parsing
            throw new IllegalArgumentException("Illegal name: " + name);
        }
        // XXX do namespace transformations

        Serializable id = context.generateNewId();

        // create the underlying main row
        Map<String, Serializable> map = new HashMap<String, Serializable>();
        map.put(model.MAIN_PRIMARY_TYPE_KEY, typeName);
        SimpleFragment mainRow = (SimpleFragment) context.createSimpleFragment(
                model.MAIN_TABLE_NAME, id, map);

        // find all schemas for this type and create fragment entities
        FragmentsMap fragments = new FragmentsMap();
        Type type = schemaManager.getDocumentType(typeName);
        if (type == null) {
            type = schemaManager.getSchema(typeName);
            if (type == null && !model.isComplexType(typeName)) {
                throw new StorageException("Unknown type: " + typeName);
            }
            // happens for any complex type not registered directly as a
            // toplevel schema, like "content", or "Name" in unit tests.
            // => no high level Type info for them
        }

        // XXX TODO XXX may not be a document type for complex props

        if (false) {
            // TODO if non-lazy creation of some fragments, create them here
            for (String schemaName : model.getTypeFragments(typeName)) {
                // TODO XXX fill in default values
                // TODO fill data instead of null XXX or just have fragments
                // empty
                Fragment fragment = context.createSimpleFragment(schemaName,
                        id, null);
                fragments.put(schemaName, fragment);
            }
        }

        // add to hierarchy table
        // TODO if folder is ordered, we have to compute the pos as max+1...
        map = new HashMap<String, Serializable>();
        map.put(model.HIER_PARENT_KEY, parent.mainFragment.getId());
        map.put(model.HIER_CHILD_NAME_KEY, name);
        map.put(model.HIER_CHILD_POS_KEY, null);
        map.put(model.HIER_CHILD_ISPROPERTY_KEY, Boolean.valueOf(complexProp));
        SimpleFragment hierRow = (SimpleFragment) context.createSimpleFragment(
                model.HIER_TABLE_NAME, id, map);
        // TODO put it in a collection context instead

        FragmentGroup rowGroup = new FragmentGroup(mainRow, hierRow, fragments);

        return new Node(type, this, context, rowGroup);
    }

    public boolean hasChildNode(Node parent, String name, boolean complexProp)
            throws StorageException {
        checkLive();
        // TODO could optimize further by not fetching the fragment at all
        return context.getChildByName(parent.getId(), name, complexProp) != null;
    }

    public Node getChildNode(Node parent, String name, boolean complexProp)
            throws StorageException {
        checkLive();
        if (name == null || name.contains("/") || name.equals(".") ||
                name.equals("..")) {
            // XXX real parsing
            throw new IllegalArgumentException("Illegal name: " + name);
        }

        // XXX namespace transformations

        // find child hier row
        Serializable parentId = parent.getId();
        SimpleFragment childHier = context.getChildByName(parentId, name,
                complexProp);
        if (childHier == null) {
            // not found
            return null;
        }
        Serializable childId = childHier.getId();

        // get main row
        SimpleFragment childMain = (SimpleFragment) context.get(
                model.MAIN_TABLE_NAME, childId, false);
        String childTypeName = (String) childMain.get(model.MAIN_PRIMARY_TYPE_KEY);
        DocumentType childType = schemaManager.getDocumentType(childTypeName);

        // TODO get all non-cached fragments at once using join / union
        FragmentsMap childFragments = new FragmentsMap();
        for (String fragmentName : model.getTypeFragments(childTypeName)) {
            Fragment fragment = context.get(fragmentName, childId, true);
            childFragments.put(fragmentName, fragment);
        }

        FragmentGroup childGroup = new FragmentGroup(childMain, childHier,
                childFragments);

        return new Node(childType, this, context, childGroup);
    }

    // TODO optimize with dedicated backend call
    public boolean hasChildren(Node parent, boolean complexProp)
            throws StorageException {
        checkLive();
        return context.getChildren(parent.getId(), complexProp).size() > 0;
    }

    public List<Node> getChildren(Node parent, boolean complexProp, String name)
            throws StorageException {
        checkLive();
        Collection<SimpleFragment> fragments = context.getChildren(
                parent.getId(), complexProp);
        List<Node> nodes;
        if (complexProp) {
            nodes = new LinkedList<Node>();
        } else {
            nodes = new ArrayList<Node>(fragments.size());
        }
        for (SimpleFragment fragment : fragments) {
            if (complexProp &&
                    !name.equals(fragment.getString(model.HIER_CHILD_NAME_KEY))) {
                continue;
            }
            Node node = getNodeById(fragment.getId());
            if (node == null) {
                // TODO what if node is null?
                throw new RuntimeException("XXX");
            }
            nodes.add(node);
        }
        return nodes;
    }

    public void removeNode(Node node) throws StorageException {
        checkLive();
        node.remove();
        // TODO XXX remove recursively the children
    }

    /*
     * ----- -----
     */

    // returns context or null if missing
    protected Context getContext(String tableName) {
        return context.getContextOrNull(tableName);
    }

    private void checkLive() throws IllegalStateException {
        if (!live) {
            throw new IllegalStateException("Session is not live");
        }
    }

    private void computeRootNode() throws StorageException {
        Long repositoryId = Long.valueOf(0L); // always 0 for now
        Serializable rootId = context.getRootId(repositoryId);
        if (rootId == null) {
            log.debug("Creating root");
            rootNode = addRootNode();
            addRootACP();
            save();
            // record information about the root id
            context.setRootId(repositoryId, rootNode.getId());
        } else {
            rootNode = getNodeById(rootId);
        }
    }

    // TODO factor with addChildNode
    private Node addRootNode() throws StorageException {
        Serializable id = context.generateNewId();

        // create the underlying main row
        Map<String, Serializable> map = new HashMap<String, Serializable>();
        map.put(model.MAIN_PRIMARY_TYPE_KEY, model.ROOT_TYPE);
        SimpleFragment mainRow = (SimpleFragment) context.createSimpleFragment(
                model.MAIN_TABLE_NAME, id, map);

        // add to hierarchy table
        map = new HashMap<String, Serializable>();
        map.put(model.HIER_PARENT_KEY, null);
        map.put(model.HIER_CHILD_POS_KEY, null);
        map.put(model.HIER_CHILD_NAME_KEY, "");
        map.put(model.HIER_CHILD_ISPROPERTY_KEY, Boolean.FALSE);
        SimpleFragment hierRow = (SimpleFragment) context.createSimpleFragment(
                model.HIER_TABLE_NAME, id, map);

        DocumentType type = schemaManager.getDocumentType(model.ROOT_TYPE);
        FragmentGroup rowGroup = new FragmentGroup(mainRow, hierRow, null);

        return new Node(type, this, context, rowGroup);
    }

    private void addRootACP() throws StorageException {
        ACLRow[] aclrows = new ACLRow[4];
        // TODO put groups in their proper place. like that now for consistency.
        aclrows[0] = new ACLRow(0, ACL.LOCAL_ACL, 0, true,
                SecurityConstants.EVERYTHING, SecurityConstants.ADMINISTRATORS,
                null);
        aclrows[1] = new ACLRow(0, ACL.LOCAL_ACL, 1, true,
                SecurityConstants.EVERYTHING, SecurityConstants.ADMINISTRATOR,
                null);
        aclrows[2] = new ACLRow(0, ACL.LOCAL_ACL, 2, true,
                SecurityConstants.READ, SecurityConstants.MEMBERS, null);
        aclrows[3] = new ACLRow(0, ACL.LOCAL_ACL, 3, true,
                SecurityConstants.VERSION, SecurityConstants.MEMBERS, null);
        rootNode.setCollectionProperty(Model.ACL_PROP, aclrows);
    }

    // public Node newNodeInstance() needed ?

    public void checkPermission(String absPath, String actions)
            throws AccessControlException, StorageException {
        checkLive();
        // TODO Auto-generated method stub
        throw new RuntimeException("Not implemented");
    }

    public boolean hasPendingChanges() throws StorageException {
        checkLive();
        // TODO Auto-generated method stub
        throw new RuntimeException("Not implemented");
    }

    public String move(String srcAbsPath, String destAbsPath)
            throws StorageException {
        checkLive();
        // TODO Auto-generated method stub
        throw new RuntimeException("Not implemented");
    }

    public String copy(Node sourceNode, String parentNode)
            throws StorageException {
        checkLive();
        // TODO Auto-generated method stub
        throw new RuntimeException("Not implemented");
    }

}
