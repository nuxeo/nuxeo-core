/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.dbs;

import static java.lang.Boolean.TRUE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_GRANT;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_PERMISSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACE_USER;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACL_NAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ACP;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ANCESTOR_IDS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_BASE_VERSION_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_CHECKED_IN;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_LATEST_MAJOR_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_LATEST_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_PROXY;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_IS_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LIFECYCLE_POLICY;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LOCK_CREATED;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_LOCK_OWNER;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_MAJOR_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_MINOR_VERSION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_MIXIN_TYPES;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_NAME;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PARENT_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PATH_INTERNAL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_POS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PREFIX;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PRIMARY_TYPE;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_IDS;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_TARGET_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_PROXY_VERSION_SERIES_ID;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_READ_ACL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_CREATED;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_DESCRIPTION;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_LABEL;
import static org.nuxeo.ecm.core.storage.dbs.DBSDocument.KEY_VERSION_SERIES_ID;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.Normalizer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelFactory;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.VersionModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.Access;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.model.Document;
import org.nuxeo.ecm.core.model.DocumentIterator;
import org.nuxeo.ecm.core.model.NoSuchDocumentException;
import org.nuxeo.ecm.core.model.Session;
import org.nuxeo.ecm.core.query.QueryException;
import org.nuxeo.ecm.core.query.QueryFilter;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.OrderByClause;
import org.nuxeo.ecm.core.query.sql.model.OrderByExpr;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.SQLQuery;
import org.nuxeo.ecm.core.query.sql.model.SelectClause;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.security.SecurityException;
import org.nuxeo.ecm.core.storage.CopyHelper;
import org.nuxeo.ecm.core.storage.ExpressionEvaluator;
import org.nuxeo.ecm.core.storage.PartialList;
import org.nuxeo.ecm.core.storage.QueryOptimizer;
import org.nuxeo.ecm.core.storage.binary.BinaryManager;
import org.nuxeo.ecm.core.storage.dbs.DBSExpressionEvaluator.OrderByComparator;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Implementation of a {@link Session} for Document-Based Storage.
 *
 * @since 5.9.4
 */
public class DBSSession implements Session {

    private static final Log log = LogFactory.getLog(DBSSession.class);

    protected final DBSRepository repository;

    protected final String sessionId;

    protected final DBSTransactionState transaction;

    protected boolean closed;

    public DBSSession(DBSRepository repository, String sessionId) {
        this.repository = repository;
        this.sessionId = sessionId;
        transaction = new DBSTransactionState(repository);
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getRepositoryName() {
        return repository.getName();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isLive() {
        return !closed;
    }

    @Override
    public void save() throws DocumentException {
        transaction.save();
        if (!TransactionHelper.isTransactionActive()) {
            transaction.commit();
        }
    }

    public void commit() throws DocumentException {
        transaction.commit();
    }

    @Override
    public boolean isStateSharedByAllThreadSessions() {
        return false;
    }

    protected BinaryManager getBinaryManager() {
        return repository.getBinaryManager();
    }

    protected String getRootId() {
        return repository.getRootId();
    }

    @Override
    public Document resolvePath(String path) throws DocumentException {
        // TODO move checks and normalize higher in call stack
        if (path == null) {
            throw new IllegalArgumentException("Null path");
        }
        int len = path.length();
        if (len == 0) {
            throw new IllegalArgumentException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new IllegalArgumentException("Relative path: " + path);
        }
        if (len > 1 && path.charAt(len - 1) == '/') {
            // remove final slash
            path = path.substring(0, len - 1);
            len--;
        }
        path = Normalizer.normalize(path, Normalizer.Form.NFKC);

        if (len == 1) {
            return getRootDocument();
        }
        DBSDocumentState state = null;
        String parentId = getRootId();
        String[] names = path.split("/", -1);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            if (name.length() == 0) {
                throw new IllegalArgumentException(
                        "Path with empty component: " + path);
            }
            state = transaction.getChildState(parentId, name);
            if (state == null) {
                throw new NoSuchDocumentException(path);
            }
            parentId = state.getId();
        }
        return getDocument(state);
    }

    protected String getDocumentIdByPath(String path) {
        // TODO move checks and normalize higher in call stack
        if (path == null) {
            throw new IllegalArgumentException("Null path");
        }
        int len = path.length();
        if (len == 0) {
            throw new IllegalArgumentException("Empty path");
        }
        if (path.charAt(0) != '/') {
            throw new IllegalArgumentException("Relative path: " + path);
        }
        if (len > 1 && path.charAt(len - 1) == '/') {
            // remove final slash
            path = path.substring(0, len - 1);
            len--;
        }
        path = Normalizer.normalize(path, Normalizer.Form.NFKC);

        if (len == 1) {
            return getRootId();
        }
        DBSDocumentState state = null;
        String parentId = getRootId();
        String[] names = path.split("/", -1);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            if (name.length() == 0) {
                throw new IllegalArgumentException(
                        "Path with empty component: " + path);
            }
            // TODO XXX add getChildId method
            state = transaction.getChildState(parentId, name);
            if (state == null) {
                return null;
            }
            parentId = state.getId();
        }
        return state.getId();
    }

    protected Document getChild(String parentId, String name)
            throws DocumentException {
        DBSDocumentState state = transaction.getChildState(parentId, name);
        return getDocument(state);
    }

    protected Iterator<Document> getChildren(String parentId)
            throws DocumentException {
        List<DBSDocumentState> states = transaction.getChildrenStates(parentId);
        if (isOrderable(parentId)) {
            // sort children in order
            Collections.sort(states, POS_COMPARATOR);
        }
        List<Document> children = new ArrayList<Document>(states.size());
        for (DBSDocumentState state : states) {
            try {
                children.add(getDocument(state));
            } catch (DocumentException e) {
                // ignore error retrieving one of the children
                // (Unknown document type)
                continue;
            }
        }
        return new DBSDocumentListIterator(children);
    }

    protected List<String> getChildrenIds(String parentId) {
        if (isOrderable(parentId)) {
            // TODO get only id and pos, not full state
            // TODO state not for update
            List<DBSDocumentState> states = transaction.getChildrenStates(parentId);
            Collections.sort(states, POS_COMPARATOR);
            List<String> children = new ArrayList<String>(states.size());
            for (DBSDocumentState state : states) {
                children.add(state.getId());
            }
            return children;
        } else {
            return transaction.getChildrenIds(parentId);
        }
    }

    protected boolean hasChildren(String parentId) {
        return transaction.hasChildren(parentId);

    }

    public static class DBSDocumentListIterator implements DocumentIterator {

        private final int size;

        private final Iterator<Document> iterator;

        public DBSDocumentListIterator(List<Document> list) {
            size = list.size();
            iterator = list.iterator();
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Document next() {
            return iterator.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    @Override
    public Document getDocumentByUUID(String id) throws DocumentException {
        Document doc = getDocument(id);
        if (doc != null) {
            return doc;
        }
        // exception required by API
        throw new NoSuchDocumentException(id);
    }

    @Override
    public Document getRootDocument() throws DocumentException {
        return getDocument(getRootId());
    }

    @Override
    public Document getNullDocument() throws DocumentException {
        return new DBSDocument(null, null, this, true);
    }

    protected Document getDocument(String id) throws DocumentException {
        return getDocument(id, true);
    }

    protected List<Document> getDocuments(List<String> ids)
            throws DocumentException {
        List<DBSDocumentState> states = transaction.getStatesForUpdate(ids);
        List<Document> docs = new ArrayList<Document>(ids.size());
        for (DBSDocumentState state : states) {
            docs.add(getDocument(state, true));
        }
        return docs;
    }

    protected Document getDocument(String id, boolean readonly)
            throws DocumentException {
        DBSDocumentState state = transaction.getStateForUpdate(id);
        return getDocument(state, readonly);
    }

    protected Document getDocument(DBSDocumentState state)
            throws DocumentException {
        return getDocument(state, true);
    }

    protected Document getDocument(DBSDocumentState state, boolean readonly)
            throws DocumentException {
        if (state == null) {
            return null;
        }
        boolean isVersion = TRUE.equals(state.get(KEY_IS_VERSION));

        String typeName = state.getPrimaryType();
        SchemaManager schemaManager = Framework.getLocalService(SchemaManager.class);
        DocumentType type = schemaManager.getDocumentType(typeName);
        if (type == null) {
            throw new DocumentException("Unknown document type: " + typeName);
        }

        if (isVersion) {
            return new DBSDocument(state, type, this, readonly);
        } else {
            return new DBSDocument(state, type, this, false);
        }
    }

    protected boolean hasChild(String parentId, String name) {
        return transaction.hasChild(parentId, name);
    }

    public Document addChild(String id, String parentId, String name, Long pos,
            String typeName) throws DocumentException {
        return addChild(id, parentId, name, pos, typeName, true);
    }

    protected Document addChild(String id, String parentId, String name,
            Long pos, String typeName, boolean readonly)
            throws DocumentException {
        DBSDocumentState state = addChildState(id, parentId, name, pos,
                typeName);
        return getDocument(state, readonly);
    }

    protected DBSDocumentState addChildState(String id, String parentId,
            String name, Long pos, String typeName) throws DocumentException {
        if (pos == null && parentId != null) {
            pos = getNextPos(parentId);
        }
        return transaction.createChild(id, parentId, name, pos, typeName);
    }

    protected boolean isOrderable(String id) {
        Map<String, Serializable> state = transaction.getStateForRead(id);
        String typeName = (String) state.get(KEY_PRIMARY_TYPE);
        SchemaManager schemaManager = Framework.getLocalService(SchemaManager.class);
        return schemaManager.getDocumentType(typeName).getFacets().contains(
                FacetNames.ORDERABLE);
    }

    protected Long getNextPos(String parentId) {
        if (!isOrderable(parentId)) {
            return null;
        }
        long max = -1;
        for (DBSDocumentState state : transaction.getChildrenStates(parentId)) {
            Long pos = (Long) state.get(KEY_POS);
            if (pos != null && pos.longValue() > max) {
                max = pos.longValue();
            }
        }
        return Long.valueOf(max + 1);
    }

    protected void orderBefore(String parentId, String sourceId, String destId)
            throws DocumentException {
        if (!isOrderable(parentId)) {
            // TODO throw exception?
            return;
        }
        if (sourceId.equals(destId)) {
            return;
        }
        // This is optimized by assuming the number of children is small enough
        // to be manageable in-memory.
        // fetch children
        List<DBSDocumentState> states = transaction.getChildrenStates(parentId);
        // sort children in order
        Collections.sort(states, POS_COMPARATOR);
        // renumber
        int i = 0;
        DBSDocumentState source = null; // source if seen
        Long destPos = null;
        for (DBSDocumentState state : states) {
            Serializable id = state.getId();
            if (id.equals(destId)) {
                destPos = Long.valueOf(i);
                i++;
                if (source != null) {
                    source.put(KEY_POS, destPos);
                }
            }
            Long setPos;
            if (id.equals(sourceId)) {
                i--;
                source = state;
                setPos = destPos;
            } else {
                setPos = Long.valueOf(i);
            }
            if (setPos != null) {
                if (!setPos.equals(state.get(KEY_POS))) {
                    state.put(KEY_POS, setPos);
                }
            }
            i++;
        }
        if (destId == null) {
            Long setPos = Long.valueOf(i);
            if (!setPos.equals(source.get(KEY_POS))) {
                source.put(KEY_POS, setPos);
            }
        }
    }

    protected void checkOut(String id) throws DocumentException {
        DBSDocumentState state = transaction.getStateForUpdate(id);
        if (!TRUE.equals(state.get(KEY_IS_CHECKED_IN))) {
            throw new DocumentException("Already checked out");
        }
        state.put(KEY_IS_CHECKED_IN, null);
    }

    protected Document checkIn(String id, String label, String checkinComment)
            throws DocumentException {
        transaction.save();
        DBSDocumentState state = transaction.getStateForUpdate(id);
        if (TRUE.equals(state.get(KEY_IS_CHECKED_IN))) {
            throw new DocumentException("Already checked in");
        }
        if (label == null) {
            // use version major + minor as label
            Long major = (Long) state.get(KEY_MAJOR_VERSION);
            Long minor = (Long) state.get(KEY_MINOR_VERSION);
            if (major == null || minor == null) {
                label = "";
            } else {
                label = major + "." + minor;
            }
        }

        // copy into a version
        DBSDocumentState verState = transaction.copy(id);
        String verId = verState.getId();
        verState.put(KEY_PARENT_ID, null);
        verState.put(KEY_ANCESTOR_IDS, null);
        verState.put(KEY_IS_VERSION, TRUE);
        verState.put(KEY_VERSION_SERIES_ID, id);
        verState.put(KEY_VERSION_CREATED, new GregorianCalendar()); // now
        verState.put(KEY_VERSION_LABEL, label);
        verState.put(KEY_VERSION_DESCRIPTION, checkinComment);
        verState.put(KEY_IS_LATEST_VERSION, TRUE);
        verState.put(KEY_IS_CHECKED_IN, null);
        verState.put(KEY_BASE_VERSION_ID, null);
        boolean isMajor = Long.valueOf(0).equals(
                verState.get(KEY_MINOR_VERSION));
        verState.put(KEY_IS_LATEST_MAJOR_VERSION, isMajor ? TRUE : null);

        // update the doc to mark it checked in
        state.put(KEY_IS_CHECKED_IN, TRUE);
        state.put(KEY_BASE_VERSION_ID, verId);

        recomputeVersionSeries(id);
        transaction.save();

        return getDocument(verId);
    }

    /**
     * Recomputes isLatest / isLatestMajor on all versions.
     */
    protected void recomputeVersionSeries(String versionSeriesId) {
        List<DBSDocumentState> states = transaction.getKeyValuedStates(
                KEY_VERSION_SERIES_ID, versionSeriesId);
        Collections.sort(states, VERSION_CREATED_COMPARATOR);
        Collections.reverse(states);
        boolean isLatest = true;
        boolean isLatestMajor = true;
        for (DBSDocumentState state : states) {
            // isLatestVersion
            state.put(KEY_IS_LATEST_VERSION, isLatest ? TRUE : null);
            isLatest = false;
            // isLatestMajorVersion
            boolean isMajor = Long.valueOf(0).equals(
                    state.get(KEY_MINOR_VERSION));
            state.put(KEY_IS_LATEST_MAJOR_VERSION,
                    isMajor && isLatestMajor ? TRUE : null);
            if (isMajor) {
                isLatestMajor = false;
            }
        }
    }

    protected void restoreVersion(Document doc, Document version) {
        String docId = doc.getUUID();
        String versionId = version.getUUID();

        DBSDocumentState state = transaction.getStateForUpdate(docId);
        Map<String, Serializable> versionState = transaction.getStateForRead(versionId);

        for (Entry<String, Serializable> en : versionState.entrySet()) {
            String key = en.getKey();
            if (!keepWhenRestore(key)) {
                state.put(key, CopyHelper.deepCopy(en.getValue()));
            }
        }
        state.put(KEY_IS_VERSION, null);
        state.put(KEY_IS_CHECKED_IN, TRUE);
        state.put(KEY_BASE_VERSION_ID, versionId);
    }

    // keys we don't copy from version when restoring
    protected boolean keepWhenRestore(String key) {
        switch (key) {
        // these are placeful stuff
        case KEY_ID:
        case KEY_PARENT_ID:
        case KEY_ANCESTOR_IDS:
        case KEY_NAME:
        case KEY_POS:
        case KEY_PRIMARY_TYPE:
        case KEY_ACP:
        case KEY_READ_ACL:
            // these are version-specific
        case KEY_VERSION_CREATED:
        case KEY_VERSION_DESCRIPTION:
        case KEY_VERSION_LABEL:
        case KEY_VERSION_SERIES_ID:
        case KEY_IS_LATEST_VERSION:
        case KEY_IS_LATEST_MAJOR_VERSION:
            // these will be updated after restore
        case KEY_IS_VERSION:
        case KEY_IS_CHECKED_IN:
        case KEY_BASE_VERSION_ID:
            return true;
        }
        return false;
    }

    @Override
    public Document copy(Document source, Document parent, String name)
            throws DocumentException {
        transaction.save();
        if (name == null) {
            name = source.getName();
        }
        name = findFreeName(parent, name);
        String sourceId = source.getUUID();
        String parentId = parent.getUUID();
        Map<String, Serializable> sourceState = transaction.getStateForRead(sourceId);
        Map<String, Serializable> parentState = transaction.getStateForRead(parentId);
        String oldParentId = (String) sourceState.get(KEY_PARENT_ID);
        Object[] parentAncestorIds = (Object[]) parentState.get(KEY_ANCESTOR_IDS);
        LinkedList<String> ancestorIds = new LinkedList<String>();
        if (parentAncestorIds != null) {
            for (Object id : parentAncestorIds) {
                ancestorIds.add((String) id);
            }
        }
        ancestorIds.add(parentId);
        if (oldParentId != null && !oldParentId.equals(parentId)) {
            if (ancestorIds.contains(sourceId)) {
                throw new DocumentException("Cannot copy a node under itself: "
                        + parentId + " is under " + sourceId);

            }
            // checkNotUnder(parentId, sourceId, "copy");
        }
        // do the copy
        String copyId = copyRecurse(sourceId, parentId, ancestorIds, name);
        DBSDocumentState copyState = transaction.getStateForUpdate(copyId);
        // version copy fixup
        if (source.isVersion()) {
            copyState.put(KEY_IS_VERSION, null);
        }
        return getDocument(copyState);
    }

    protected String copyRecurse(String sourceId, String parentId,
            LinkedList<String> ancestorIds, String name) {
        String copyId = copy(sourceId, parentId, ancestorIds, name);
        ancestorIds.addLast(copyId);
        for (String childId : getChildrenIds(sourceId)) {
            copyRecurse(childId, copyId, ancestorIds, null);
        }
        ancestorIds.removeLast();
        return copyId;
    }

    /**
     * Copy source under parent, and set its ancestors.
     */
    protected String copy(String sourceId, String parentId,
            List<String> ancestorIds, String name) {
        DBSDocumentState copy = transaction.copy(sourceId);
        copy.put(KEY_PARENT_ID, parentId);
        copy.put(KEY_ANCESTOR_IDS,
                ancestorIds.toArray(new Object[ancestorIds.size()]));
        if (name != null) {
            copy.put(KEY_NAME, name);
        }
        copy.put(KEY_BASE_VERSION_ID, null);
        copy.put(KEY_IS_CHECKED_IN, null);
        return copy.getId();
    }

    protected static final Pattern dotDigitsPattern = Pattern.compile("(.*)\\.[0-9]+$");

    protected String findFreeName(Document parent, String name) {
        if (hasChild(parent.getUUID(), name)) {
            Matcher m = dotDigitsPattern.matcher(name);
            if (m.matches()) {
                // remove trailing dot and digits
                name = m.group(1);
            }
            // add dot + unique digits
            name += "." + System.currentTimeMillis();
        }
        return name;
    }

    /** Checks that we don't move/copy under ourselves. */
    protected void checkNotUnder(String parentId, String id, String op)
            throws DocumentException {
        // TODO use ancestors
        String pid = parentId;
        do {
            if (pid.equals(id)) {
                throw new DocumentException("Cannot " + op
                        + " a node under itself: " + parentId + " is under "
                        + id);
            }
            Map<String, Serializable> state = transaction.getStateForRead(pid);
            if (state == null) {
                // cannot happen
                throw new DocumentException("No parent: " + pid);
            }
            pid = (String) state.get(KEY_PARENT_ID);
        } while (pid != null);
    }

    @Override
    public Document move(Document source, Document parent, String name)
            throws DocumentException {
        String oldName = (String) source.getName();
        if (name == null) {
            name = oldName;
        }
        String sourceId = source.getUUID();
        String parentId = parent.getUUID();
        DBSDocumentState sourceState = transaction.getStateForUpdate(sourceId);
        String oldParentId = (String) sourceState.get(KEY_PARENT_ID);

        // simple case of a rename
        if (ObjectUtils.equals(oldParentId, parentId)) {
            if (!oldName.equals(name)) {
                if (hasChild(parentId, name)) {
                    throw new DocumentException(
                            "Destination name already exists: " + name);
                }
                // do the move
                sourceState.put(KEY_NAME, name);
                // no ancestors to change
            }
            return source;
        } else {
            // if not just a simple rename, flush
            transaction.save();
            if (hasChild(parentId, name)) {
                throw new DocumentException("Destination name already exists: "
                        + name);
            }
        }

        // prepare new ancestor ids
        Map<String, Serializable> parentState = transaction.getStateForRead(parentId);
        Object[] parentAncestorIds = (Object[]) parentState.get(KEY_ANCESTOR_IDS);
        List<String> ancestorIdsList = new ArrayList<String>();
        if (parentAncestorIds != null) {
            for (Object id : parentAncestorIds) {
                ancestorIdsList.add((String) id);
            }
        }
        ancestorIdsList.add(parentId);
        Object[] ancestorIds = ancestorIdsList.toArray(new Object[ancestorIdsList.size()]);

        if (ancestorIdsList.contains(sourceId)) {
            throw new DocumentException("Cannot move a node under itself: "
                    + parentId + " is under " + sourceId);
        }

        // do the move
        sourceState.put(KEY_NAME, name);
        sourceState.put(KEY_PARENT_ID, parentId);

        // update ancestors on all sub-children
        Object[] oldAncestorIds = (Object[]) sourceState.get(KEY_ANCESTOR_IDS);
        int ndel = oldAncestorIds == null ? 0 : oldAncestorIds.length;
        transaction.updateAncestors(sourceId, ndel, ancestorIds);

        return source;
    }

    /**
     * Removes a document.
     * <p>
     * We also have to update everything impacted by "relations":
     * <ul>
     * <li>parent-child relations: delete all subchildren recursively,
     * <li>proxy-target relations: if a proxy is removed, update the target's
     * PROXY_IDS; and if a target is removed, raise an error if a proxy still
     * exists for that target.
     * </ul>
     */
    protected void remove(String id) throws DocumentException {
        transaction.save();

        Map<String, Serializable> state = transaction.getStateForRead(id);
        String versionSeriesId;
        if (TRUE.equals(state.get(KEY_IS_VERSION))) {
            versionSeriesId = (String) state.get(KEY_VERSION_SERIES_ID);
        } else {
            versionSeriesId = null;
        }
        // find all sub-docs and whether they're proxies
        Map<String, String> proxyTargets = new HashMap<>();
        Map<String, Object[]> targetProxies = new HashMap<>();
        Set<String> removedIds = transaction.getSubTree(id, proxyTargets,
                targetProxies);

        // add this node
        removedIds.add(id);
        if (TRUE.equals(KEY_IS_PROXY)) {
            String targetId = (String) state.get(KEY_PROXY_TARGET_ID);
            proxyTargets.put(id, targetId);
        }
        Object[] proxyIds = (Object[]) state.get(KEY_PROXY_IDS);
        if (proxyIds != null) {
            targetProxies.put(id, proxyIds);
        }

        // if a proxy target is removed, check that all proxies to it
        // are removed
        for (Entry<String, Object[]> en : targetProxies.entrySet()) {
            String targetId = en.getKey();
            if (!removedIds.contains(targetId)) {
                continue;
            }
            for (Object proxyId : en.getValue()) {
                if (!removedIds.contains(proxyId)) {
                    throw new DocumentException("Cannot remove " + id
                            + ", subdocument " + targetId
                            + " is the target of proxy " + proxyId);
                }
            }
        }

        // remove all docs
        for (String cid : removedIds) {
            transaction.removeState(cid);
        }

        // fix proxies back-pointers on proxy targets
        Set<String> targetIds = new HashSet<>(proxyTargets.values());
        for (String targetId : targetIds) {
            if (removedIds.contains(targetId)) {
                // the target was also removed, skip
                continue;
            }
            DBSDocumentState target = transaction.getStateForUpdate(targetId);
            removeBackProxyIds(target, removedIds);
        }

        // recompute version series if needed
        // only done for root of deletion as versions are not fileable
        if (versionSeriesId != null) {
            recomputeVersionSeries(versionSeriesId);
        }
    }

    @Override
    public Document createProxy(Document doc, Document folder)
            throws DocumentException {
        String id = doc.getUUID();
        String targetId;
        String versionSeriesId;
        if (doc.isVersion()) {
            targetId = id;
            versionSeriesId = doc.getVersionSeriesId();
        } else if (doc.isProxy()) {
            // copy the proxy
            Map<String, Serializable> state = transaction.getStateForRead(id);
            targetId = (String) state.get(KEY_PROXY_TARGET_ID);
            versionSeriesId = (String) state.get(KEY_PROXY_VERSION_SERIES_ID);
        } else {
            // working copy (live document)
            targetId = id;
            versionSeriesId = targetId;
        }

        String parentId = folder.getUUID();
        String name = findFreeName(folder, doc.getName());
        Long pos = parentId == null ? null : getNextPos(parentId);

        DBSDocumentState state = addProxyState(null, parentId, name, pos,
                targetId, versionSeriesId);
        return getDocument(state);
    }

    protected DBSDocumentState addProxyState(String id, String parentId,
            String name, Long pos, String targetId, String versionSeriesId)
            throws DocumentException {
        DBSDocumentState target = transaction.getStateForUpdate(targetId);
        String typeName = (String) target.get(KEY_PRIMARY_TYPE);

        DBSDocumentState proxy = transaction.createChild(id, parentId, name,
                pos, typeName);
        String proxyId = proxy.getId();
        proxy.put(KEY_IS_PROXY, TRUE);
        proxy.put(KEY_PROXY_TARGET_ID, targetId);
        proxy.put(KEY_PROXY_VERSION_SERIES_ID, versionSeriesId);
        proxy.put(KEY_IS_VERSION, null);
        proxy.put(KEY_BASE_VERSION_ID, null);
        proxy.put(KEY_VERSION_SERIES_ID, versionSeriesId);

        // copy target state to proxy
        transaction.updateProxy(target, proxyId);

        // add back-reference to proxy on target
        addBackProxyId(target, proxyId);

        return transaction.getStateForUpdate(proxyId);
    }

    protected void addBackProxyId(DBSDocumentState state, String id) {
        Object[] proxyIds = (Object[]) state.get(KEY_PROXY_IDS);
        Object[] newProxyIds;
        if (proxyIds == null) {
            newProxyIds = new Object[] { id };
        } else {
            newProxyIds = new Object[proxyIds.length + 1];
            System.arraycopy(proxyIds, 0, newProxyIds, 0, proxyIds.length);
            newProxyIds[proxyIds.length] = id;
        }
        state.put(KEY_PROXY_IDS, newProxyIds);
    }

    protected void removeBackProxyId(DBSDocumentState state, String id) {
        removeBackProxyIds(state, Collections.singleton(id));
    }

    protected void removeBackProxyIds(DBSDocumentState state, Set<String> ids) {
        Object[] proxyIds = (Object[]) state.get(KEY_PROXY_IDS);
        if (proxyIds == null) {
            return;
        }
        List<Object> keepIds = new ArrayList<>(proxyIds.length);
        for (Object pid : proxyIds) {
            if (!ids.contains(pid)) {
                keepIds.add(pid);
            }
        }
        Object[] newProxyIds = keepIds.isEmpty() ? null
                : keepIds.toArray(new Object[keepIds.size()]);
        state.put(KEY_PROXY_IDS, newProxyIds);
    }

    @Override
    public Collection<Document> getProxies(Document doc, Document folder)
            throws DocumentException {
        List<DBSDocumentState> states;
        String docId = doc.getUUID();
        if (doc.isVersion()) {
            states = transaction.getKeyValuedStates(KEY_PROXY_TARGET_ID, docId);
        } else {
            String versionSeriesId;
            if (doc.isProxy()) {
                Map<String, Serializable> state = transaction.getStateForRead(docId);
                versionSeriesId = (String) state.get(KEY_PROXY_VERSION_SERIES_ID);
            } else {
                versionSeriesId = docId;
            }
            states = transaction.getKeyValuedStates(
                    KEY_PROXY_VERSION_SERIES_ID, versionSeriesId);
        }

        String parentId = folder == null ? null : folder.getUUID();
        List<Document> documents = new ArrayList<Document>(states.size());
        for (DBSDocumentState state : states) {
            // filter by parent
            if (parentId != null && !parentId.equals(state.getParentId())) {
                continue;
            }
            documents.add(getDocument(state));
        }
        return documents;
    }

    @Override
    public void setProxyTarget(Document proxy, Document target)
            throws DocumentException {
        String proxyId = proxy.getUUID();
        String targetId = target.getUUID();
        DBSDocumentState proxyState = transaction.getStateForUpdate(proxyId);
        String oldTargetId = (String) proxyState.get(KEY_PROXY_TARGET_ID);

        // update old target's back-pointers: remove proxy id
        DBSDocumentState oldTargetState = transaction.getStateForUpdate(oldTargetId);
        removeBackProxyId(oldTargetState, proxyId);
        // update new target's back-pointers: add proxy id
        DBSDocumentState targetState = transaction.getStateForUpdate(targetId);
        addBackProxyId(targetState, proxyId);
        // set new target
        proxyState.put(KEY_PROXY_TARGET_ID, targetId);
    }

    @Override
    public Document importDocument(String id, Document parent, String name,
            String typeName, Map<String, Serializable> properties)
            throws DocumentException {
        String parentId = parent == null ? null : parent.getUUID();
        boolean isProxy = typeName.equals(CoreSession.IMPORT_PROXY_TYPE);
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        Long pos = null; // TODO pos
        DBSDocumentState state;
        if (isProxy) {
            // check that target exists and find its typeName
            String targetId = (String) properties.get(CoreSession.IMPORT_PROXY_TARGET_ID);
            if (targetId == null) {
                throw new DocumentException("Cannot import proxy " + id
                        + " with null target");
            }
            Map<String, Serializable> targetState = transaction.getStateForRead(targetId);
            if (targetState == null) {
                throw new DocumentException("Cannot import proxy " + id
                        + " with missing target " + targetId);
            }
            String versionSeriesId = (String) properties.get(CoreSession.IMPORT_PROXY_VERSIONABLE_ID);
            state = addProxyState(id, parentId, name, pos, targetId,
                    versionSeriesId);
        } else {
            // version & live document
            props.put(KEY_LIFECYCLE_POLICY,
                    properties.get(CoreSession.IMPORT_LIFECYCLE_POLICY));
            props.put(KEY_LIFECYCLE_STATE,
                    properties.get(CoreSession.IMPORT_LIFECYCLE_STATE));
            // compat with old lock import
            @SuppressWarnings("deprecation")
            String key = (String) properties.get(CoreSession.IMPORT_LOCK);
            if (key != null) {
                String[] values = key.split(":");
                if (values.length == 2) {
                    String owner = values[0];
                    Calendar created = new GregorianCalendar();
                    try {
                        created.setTimeInMillis(DateFormat.getDateInstance(
                                DateFormat.MEDIUM).parse(values[1]).getTime());
                    } catch (ParseException e) {
                        // use current date
                    }
                    props.put(KEY_LOCK_OWNER, owner);
                    props.put(KEY_LOCK_CREATED, created);
                }
            }

            Serializable importLockOwnerProp = properties.get(CoreSession.IMPORT_LOCK_OWNER);
            if (importLockOwnerProp != null) {
                props.put(KEY_LOCK_OWNER, importLockOwnerProp);
            }
            Serializable importLockCreatedProp = properties.get(CoreSession.IMPORT_LOCK_CREATED);
            if (importLockCreatedProp != null) {
                props.put(KEY_LOCK_CREATED, importLockCreatedProp);
            }

            props.put(KEY_MAJOR_VERSION,
                    properties.get(CoreSession.IMPORT_VERSION_MAJOR));
            props.put(KEY_MINOR_VERSION,
                    properties.get(CoreSession.IMPORT_VERSION_MINOR));
            Boolean isVersion = trueOrNull(properties.get(CoreSession.IMPORT_IS_VERSION));
            props.put(KEY_IS_VERSION, isVersion);
            if (TRUE.equals(isVersion)) {
                // version
                props.put(
                        KEY_VERSION_SERIES_ID,
                        properties.get(CoreSession.IMPORT_VERSION_VERSIONABLE_ID));
                props.put(KEY_VERSION_CREATED,
                        properties.get(CoreSession.IMPORT_VERSION_CREATED));
                props.put(KEY_VERSION_LABEL,
                        properties.get(CoreSession.IMPORT_VERSION_LABEL));
                props.put(KEY_VERSION_DESCRIPTION,
                        properties.get(CoreSession.IMPORT_VERSION_DESCRIPTION));
                // TODO maybe these should be recomputed at end of import:
                props.put(
                        KEY_IS_LATEST_VERSION,
                        trueOrNull(properties.get(CoreSession.IMPORT_VERSION_IS_LATEST)));
                props.put(
                        KEY_IS_LATEST_MAJOR_VERSION,
                        trueOrNull(properties.get(CoreSession.IMPORT_VERSION_IS_LATEST_MAJOR)));
            } else {
                // live document
                props.put(KEY_BASE_VERSION_ID,
                        properties.get(CoreSession.IMPORT_BASE_VERSION_ID));
                props.put(
                        KEY_IS_CHECKED_IN,
                        trueOrNull(properties.get(CoreSession.IMPORT_CHECKED_IN)));
            }
            state = addChildState(id, parentId, name, pos, typeName);
        }
        for (Entry<String, Serializable> entry : props.entrySet()) {
            state.put(entry.getKey(), entry.getValue());
        }
        return getDocument(state, false); // not readonly
    }

    protected static Boolean trueOrNull(Object value) {
        return TRUE.equals(value) ? TRUE : null;
    }

    @Override
    public Document getVersion(String versionSeriesId, VersionModel versionModel)
            throws DocumentException {
        DBSDocumentState state = getVersionByLabel(versionSeriesId,
                versionModel.getLabel());
        if (state == null) {
            return null;
        }
        versionModel.setDescription((String) state.get(KEY_VERSION_DESCRIPTION));
        versionModel.setCreated((Calendar) state.get(KEY_VERSION_CREATED));
        return getDocument(state);
    }

    protected DBSDocumentState getVersionByLabel(String versionSeriesId,
            String label) {
        List<DBSDocumentState> states = transaction.getKeyValuedStates(
                KEY_VERSION_SERIES_ID, versionSeriesId);
        for (DBSDocumentState state : states) {
            if (label.equals(state.get(KEY_VERSION_LABEL))) {
                return state;
            }
        }
        return null;
    }

    protected List<String> getVersionsIds(String versionSeriesId) {
        // order by creation date
        List<DBSDocumentState> states = transaction.getKeyValuedStates(
                KEY_VERSION_SERIES_ID, versionSeriesId);
        Collections.sort(states, VERSION_CREATED_COMPARATOR);
        List<String> ids = new ArrayList<String>(states.size());
        for (DBSDocumentState state : states) {
            ids.add(state.getId());
        }
        return ids;
    }

    protected Document getLastVersion(String versionSeriesId)
            throws DocumentException {
        List<DBSDocumentState> states = transaction.getKeyValuedStates(
                KEY_VERSION_SERIES_ID, versionSeriesId);
        if (states.isEmpty()) {
            return null;
        }
        // find latest one
        Calendar latest = null;
        DBSDocumentState latestState = null;
        for (DBSDocumentState state : states) {
            Calendar created = (Calendar) state.get(KEY_VERSION_CREATED);
            if (latest == null || created.compareTo(latest) > 0) {
                latest = created;
                latestState = state;
            }
        }
        return getDocument(latestState);
    }

    private static final Comparator<DBSDocumentState> VERSION_CREATED_COMPARATOR = new Comparator<DBSDocumentState>() {
        @Override
        public int compare(DBSDocumentState s1, DBSDocumentState s2) {
            Calendar c1 = (Calendar) s1.get(KEY_VERSION_CREATED);
            Calendar c2 = (Calendar) s2.get(KEY_VERSION_CREATED);
            if (c1 == null && c2 == null) {
                // coherent sort
                return s1.hashCode() - s2.hashCode();
            }
            if (c1 == null) {
                return 1;
            }
            if (c2 == null) {
                return -1;
            }
            return c1.compareTo(c2);
        }
    };

    private static final Comparator<DBSDocumentState> POS_COMPARATOR = new Comparator<DBSDocumentState>() {
        @Override
        public int compare(DBSDocumentState s1, DBSDocumentState s2) {
            Long p1 = (Long) s1.get(KEY_POS);
            Long p2 = (Long) s2.get(KEY_POS);
            if (p1 == null && p2 == null) {
                // coherent sort
                return s1.hashCode() - s2.hashCode();
            }
            if (p1 == null) {
                return 1;
            }
            if (p2 == null) {
                return -1;
            }
            return p1.compareTo(p2);
        }
    };

    // TODO move logic higher
    @Override
    public ACP getMergedACP(Document doc) throws SecurityException {
        try {
            Document base = doc.isVersion() ? doc.getSourceDocument() : doc;
            if (base == null) {
                return null;
            }
            ACP acp = getACP(base);
            if (doc.getParent() == null) {
                return acp;
            }
            // get inherited ACLs only if no blocking inheritance ACE exists
            // in the top level ACP.
            ACL acl = null;
            if (acp == null
                    || acp.getAccess(SecurityConstants.EVERYONE,
                            SecurityConstants.EVERYTHING) != Access.DENY) {
                acl = getInheritedACLs(doc);
            }
            if (acp == null) {
                if (acl == null) {
                    return null;
                }
                acp = new ACPImpl();
            }
            if (acl != null) {
                acp.addACL(acl);
            }
            return acp;
        } catch (DocumentException e) {
            throw new SecurityException("Failed to get merged acp", e);
        }
    }

    protected ACL getInheritedACLs(Document doc) throws DocumentException {
        doc = doc.getParent();
        ACL merged = null;
        while (doc != null) {
            ACP acp = getACP(doc);
            if (acp != null) {
                ACL acl = acp.getMergedACLs(ACL.INHERITED_ACL);
                if (merged == null) {
                    merged = acl;
                } else {
                    merged.addAll(acl);
                }
                if (acp.getAccess(SecurityConstants.EVERYONE,
                        SecurityConstants.EVERYTHING) == Access.DENY) {
                    break;
                }
            }
            doc = doc.getParent();
        }
        return merged;
    }

    protected ACP getACP(Document doc) throws SecurityException {
        Map<String, Serializable> state = transaction.getStateForRead(doc.getUUID());
        return memToAcp(state.get(KEY_ACP));
    }

    @Override
    public void setACP(Document doc, ACP acp, boolean overwrite)
            throws SecurityException {
        if (!overwrite) {
            if (acp == null) {
                return;
            }
            // merge with existing
            acp = updateACP(getACP(doc), acp);
        }
        String id = doc.getUUID();
        DBSDocumentState state = transaction.getStateForUpdate(id);
        state.put(KEY_ACP, acpToMem(acp));
        transaction.updateReadAcls(id);
    }

    /**
     * Returns the merge of two ACPs.
     */
    // TODO move to ACPImpl
    protected static ACP updateACP(ACP curAcp, ACP addAcp) {
        if (curAcp == null) {
            return addAcp;
        }
        ACP newAcp = curAcp.clone();
        Map<String, ACL> acls = new HashMap<String, ACL>();
        for (ACL acl : newAcp.getACLs()) {
            String name = acl.getName();
            if (ACL.INHERITED_ACL.equals(name)) {
                throw new IllegalStateException(curAcp.toString());
            }
            acls.put(name, acl);
        }
        for (ACL acl : addAcp.getACLs()) {
            String name = acl.getName();
            if (ACL.INHERITED_ACL.equals(name)) {
                continue;
            }
            ACL curAcl = acls.get(name);
            if (curAcl != null) {
                // TODO avoid duplicates
                curAcl.addAll(acl);
            } else {
                newAcp.addACL(acl);
            }
        }
        return newAcp;
    }

    protected static Serializable acpToMem(ACP acp) {
        if (acp == null) {
            return null;
        }
        ACL[] acls = acp.getACLs();
        if (acls.length == 0) {
            return null;
        }
        List<Serializable> aclList = new ArrayList<Serializable>(acls.length);
        for (ACL acl : acls) {
            String name = acl.getName();
            if (name.equals(ACL.INHERITED_ACL)) {
                continue;
            }
            ACE[] aces = acl.getACEs();
            List<Serializable> aceList = new ArrayList<Serializable>(
                    aces.length);
            for (ACE ace : aces) {
                Map<String, Serializable> aceMap = new HashMap<String, Serializable>();
                aceMap.put(KEY_ACE_USER, ace.getUsername());
                aceMap.put(KEY_ACE_PERMISSION, ace.getPermission());
                aceMap.put(KEY_ACE_GRANT, Boolean.valueOf(ace.isGranted()));
                aceList.add((Serializable) aceMap);
            }
            Map<String, Serializable> aclMap = new HashMap<String, Serializable>();
            aclMap.put(KEY_ACL_NAME, name);
            aclMap.put(KEY_ACL, (Serializable) aceList);
            aclList.add((Serializable) aclMap);
        }
        return (Serializable) aclList;
    }

    protected static ACP memToAcp(Serializable acpSer) {
        if (acpSer == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Serializable> aclList = (List<Serializable>) acpSer;
        ACP acp = new ACPImpl();
        for (Serializable aclSer : aclList) {
            @SuppressWarnings("unchecked")
            Map<String, Serializable> aclMap = (Map<String, Serializable>) aclSer;
            String name = (String) aclMap.get(KEY_ACL_NAME);
            @SuppressWarnings("unchecked")
            List<Serializable> aceList = (List<Serializable>) aclMap.get(KEY_ACL);
            ACL acl = new ACLImpl(name);
            for (Serializable aceSer : aceList) {
                @SuppressWarnings("unchecked")
                Map<String, Serializable> aceMap = (Map<String, Serializable>) aceSer;
                String username = (String) aceMap.get(KEY_ACE_USER);
                String permission = (String) aceMap.get(KEY_ACE_PERMISSION);
                Boolean granted = (Boolean) aceMap.get(KEY_ACE_GRANT);
                ACE ace = new ACE(username, permission, granted.booleanValue());
                acl.add(ace);
            }
            acp.addACL(acl);
        }
        return acp;
    }

    @Override
    public Map<String, String> getBinaryFulltext(Serializable id)
            throws DocumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }

    @Override
    public DocumentModelList query(String query, String queryType,
            QueryFilter queryFilter, long countUpTo) throws QueryException {
        // query
        PartialList<String> pl = doQuery(query, queryType, queryFilter,
                (int) countUpTo);

        // get Documents in bulk
        List<Document> docs;
        try {
            docs = getDocuments(pl.list);
        } catch (DocumentException e) {
            throw new QueryException(e);
        }

        // build DocumentModels from Documents
        String[] schemas = { "common" };
        List<DocumentModel> list = new ArrayList<DocumentModel>(docs.size());
        for (Document doc : docs) {
            try {
                list.add(DocumentModelFactory.createDocumentModel(doc, schemas));
            } catch (DocumentException e) {
                log.error("Could not create document model for doc: " + doc, e);
            }
        }

        return new DocumentModelListImpl(list, pl.totalSize);
    }

    protected PartialList<String> doQuery(String query, String queryType,
            QueryFilter queryFilter, int countUpTo) throws QueryException {
        PartialList<Map<String, Serializable>> pl = doQueryAndFetch(query,
                queryType, queryFilter, countUpTo, true);
        List<String> ids = new ArrayList<String>(pl.list.size());
        for (Map<String, Serializable> map : pl.list) {
            String id = (String) map.get(NXQL.ECM_UUID);
            ids.add(id);
        }
        return new PartialList<String>(ids, pl.totalSize);
    }

    protected PartialList<Map<String, Serializable>> doQueryAndFetch(
            String query, String queryType, QueryFilter queryFilter,
            int countUpTo) throws QueryException {
        return doQueryAndFetch(query, queryType, queryFilter, countUpTo, false);
    }

    protected PartialList<Map<String, Serializable>> doQueryAndFetch(
            String query, String queryType, QueryFilter queryFilter,
            int countUpTo, boolean onlyId) throws QueryException {
        if (!NXQL.NXQL.equals(queryType)) {
            throw new QueryException("No QueryMaker accepts query type: "
                    + queryType);
        }
        // transform the query according to the transformers defined by the
        // security policies
        SQLQuery sqlQuery = SQLQueryParser.parse(query);
        for (SQLQuery.Transformer transformer : queryFilter.getQueryTransformers()) {
            sqlQuery = transformer.transform(queryFilter.getPrincipal(),
                    sqlQuery);
        }
        OrderByClause orderByClause = sqlQuery.orderBy;

        if (onlyId) {
            sqlQuery.select = new SelectClause();
            sqlQuery.select.add(new Reference(NXQL.ECM_UUID));
        }

        MultiExpression expression = new QueryOptimizer().getOptimizedQuery(
                sqlQuery, queryFilter.getFacetFilter());
        DBSExpressionEvaluator evaluator = new DBSExpressionEvaluator(this,
                expression, queryFilter.getPrincipals());

        // query in-memory in saved state
        List<Map<String, Serializable>> states = new ArrayList<>();
        Set<String> done = new HashSet<>();
        for (DBSDocumentState state : transaction.savedStates.values()) {
            String id = state.getId();
            done.add(id);
            if (transaction.savedDeleted.contains(id)) {
                // deleted
                continue;
            }
            if (evaluator.matches(state)) {
                states.add(state.getMap());
            }
        }
        boolean postFilter = !states.isEmpty() || isOrderByPath(orderByClause);

        int limit = (int) queryFilter.getLimit();
        int offset = (int) queryFilter.getOffset();
        if (offset < 0) {
            offset = 0;
        }
        if (limit < 0) {
            limit = 0;
        }

        int repoLimit;
        int repoOffset;
        OrderByClause repoOrderByClause;
        if (postFilter) {
            // we have to merge ordering and batching between memory and
            // repository
            repoLimit = 0;
            repoOffset = 0;
            repoOrderByClause = null;
        } else {
            // fast case, we can use the repository query directly
            repoLimit = limit;
            repoOffset = offset;
            repoOrderByClause = orderByClause;
        }

        // query the repository
        boolean deepCopy = !onlyId;
        PartialList<Map<String, Serializable>> pl = repository.queryAndFetch(
                expression, evaluator, repoOrderByClause, repoLimit,
                repoOffset, countUpTo, deepCopy, done);

        long totalSize;
        if (pl.totalSize < 0) {
            totalSize = pl.totalSize;
        } else {
            totalSize = states.size() + pl.totalSize;
            if (countUpTo == -1) {
                // count full size
            } else if (countUpTo == 0) {
                // no count
                totalSize = -1; // not counted
            } else {
                // count only if less than countUpTo
                if (totalSize > countUpTo) {
                    totalSize = -2; // truncated
                }
            }
        }

        if (states.isEmpty()) {
            states = pl.list;
        } else {
            states.addAll(pl.list);
        }

        if (postFilter) {
            // ORDER BY
            if (orderByClause != null) {
                doOrderBy(states, orderByClause, evaluator);
            }
            // LIMIT / OFFSET
            if (limit != 0) {
                int size = states.size();
                states.subList(0, offset > size ? size : offset).clear();
                size = states.size();
                if (limit < size) {
                    states.subList(limit, size).clear();
                }
            }
        }

        List<Map<String, Serializable>> flatList;
        if (onlyId) {
            // optimize because we just need the id
            flatList = new ArrayList<>(states.size());
            for (Map<String, Serializable> state : states) {
                flatList.add(Collections.singletonMap(NXQL.ECM_UUID,
                        state.get(KEY_ID)));
            }
        } else {
            flatList = flatten(states);
        }

        return new PartialList<Map<String, Serializable>>(flatList, totalSize);
    }

    /** Does an ORDER BY clause include ecm:path */
    protected boolean isOrderByPath(OrderByClause orderByClause) {
        if (orderByClause == null) {
            return false;
        }
        for (OrderByExpr ob : orderByClause.elements) {
            if (ob.reference.name.equals(NXQL.ECM_PATH)) {
                return true;
            }
        }
        return false;
    }

    protected String getPath(Map<String, Serializable> state) {
        String name = (String) state.get(KEY_NAME);
        String parentId = (String) state.get(KEY_PARENT_ID);
        state = transaction.getStateForRead(parentId);
        if (state == null) {
            if ("".equals(name)) {
                return "/"; // root
            } else {
                return name; // placeless, no slash
            }
        }
        LinkedList<String> list = new LinkedList<String>();
        list.addFirst(name);
        while (state != null) {
            name = (String) state.get(KEY_NAME);
            parentId = (String) state.get(KEY_PARENT_ID);
            list.addFirst(name);
            state = transaction.getStateForRead(parentId);
        }
        return StringUtils.join(list, '/');
    }

    protected void doOrderBy(List<Map<String, Serializable>> states,
            OrderByClause orderByClause, DBSExpressionEvaluator evaluator) {
        if (isOrderByPath(orderByClause)) {
            // add path info to do the sort
            for (Map<String, Serializable> state : states) {
                state.put(KEY_PATH_INTERNAL, getPath(state));
            }
        }
        Collections.sort(states,
                new OrderByComparator(orderByClause, evaluator));
    }

    /**
     * Flatten and convert from internal names to NXQL.
     */
    protected List<Map<String, Serializable>> flatten(
            List<Map<String, Serializable>> states) {
        List<Map<String, Serializable>> flatList = new ArrayList<>(
                states.size());
        for (Map<String, Serializable> state : states) {
            flatList.add(flatten(state));
        }
        return flatList;
    }

    protected Map<String, Serializable> flatten(Map<String, Serializable> state) {
        Map<String, Serializable> flat = new HashMap<>();
        for (Entry<String, Serializable> en : state.entrySet()) {
            String key = en.getKey();
            Serializable value = en.getValue();
            String name;
            if (key.startsWith(KEY_PREFIX)) {
                name = convToNXQL(key);
                if (name == null) {
                    // present in state but not returned to caller
                    continue;
                }
            } else {
                name = key;
            }
            // TODO XXX complex props
            flat.put(name, value);
        }
        return flat;
    }

    @Override
    public IterableQueryResult queryAndFetch(String query, String queryType,
            QueryFilter queryFilter, Object[] params) throws QueryException {
        int countUpTo = -1;
        PartialList<Map<String, Serializable>> pl = doQueryAndFetch(query,
                queryType, queryFilter, countUpTo);
        return new DBSQueryResult(pl);
    }

    protected static class DBSQueryResult implements IterableQueryResult,
            Iterator<Map<String, Serializable>> {

        boolean closed;

        protected List<Map<String, Serializable>> maps;

        protected long totalSize;

        protected long pos;

        protected DBSQueryResult(PartialList<Map<String, Serializable>> maps) {
            this.maps = maps.list;
            this.totalSize = maps.totalSize;
        }

        @Override
        public Iterator<Map<String, Serializable>> iterator() {
            return this;
        }

        @Override
        public void close() {
            closed = true;
            pos = -1;
        }

        @Override
        public boolean isLife() {
            return !closed;
        }

        @Override
        public long size() {
            return totalSize;
        }

        @Override
        public long pos() {
            return pos;
        }

        @Override
        public void skipTo(long pos) {
            if (pos < 0) {
                pos = 0;
            } else if (pos > totalSize) {
                pos = totalSize;
            }
            this.pos = pos;
        }

        @Override
        public boolean hasNext() {
            return pos < totalSize - 1;
        }

        @Override
        public Map<String, Serializable> next() {
            if (closed || pos == totalSize) {
                throw new NoSuchElementException();
            }
            Map<String, Serializable> map = maps.get((int) pos);
            pos++;
            return map;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public static String convToInternal(String name) {
        switch (name) {
        case NXQL.ECM_UUID:
            return KEY_ID;
        case NXQL.ECM_NAME:
            return KEY_NAME;
        case NXQL.ECM_POS:
            return KEY_POS;
        case NXQL.ECM_PARENTID:
            return KEY_PARENT_ID;
        case NXQL.ECM_MIXINTYPE:
            return KEY_MIXIN_TYPES;
        case NXQL.ECM_PRIMARYTYPE:
            return KEY_PRIMARY_TYPE;
        case NXQL.ECM_ISPROXY:
            return KEY_IS_PROXY;
        case NXQL.ECM_ISVERSION:
        case NXQL.ECM_ISVERSION_OLD:
            return KEY_IS_VERSION;
        case NXQL.ECM_LIFECYCLESTATE:
            return KEY_LIFECYCLE_STATE;
        case NXQL.ECM_FULLTEXT:
        case NXQL.ECM_FULLTEXT_JOBID:
        case NXQL.ECM_TAG:
            throw new UnsupportedOperationException(name);
        case NXQL.ECM_LOCK_OWNER:
            return KEY_LOCK_OWNER;
        case NXQL.ECM_LOCK_CREATED:
            return KEY_LOCK_CREATED;
        case NXQL.ECM_PROXY_TARGETID:
            return KEY_PROXY_TARGET_ID;
        case NXQL.ECM_PROXY_VERSIONABLEID:
            return KEY_PROXY_VERSION_SERIES_ID;
        case NXQL.ECM_ISCHECKEDIN:
            return KEY_IS_CHECKED_IN;
        case NXQL.ECM_ISLATESTVERSION:
            return KEY_IS_LATEST_VERSION;
        case NXQL.ECM_ISLATESTMAJORVERSION:
            return KEY_IS_LATEST_MAJOR_VERSION;
        case NXQL.ECM_VERSIONLABEL:
            return KEY_VERSION_LABEL;
        case NXQL.ECM_VERSIONCREATED:
            return KEY_VERSION_CREATED;
        case NXQL.ECM_VERSIONDESCRIPTION:
            return KEY_VERSION_DESCRIPTION;
        case NXQL.ECM_VERSION_VERSIONABLEID:
            return KEY_VERSION_SERIES_ID;
        case ExpressionEvaluator.NXQL_ECM_ANCESTOR_IDS:
            return KEY_ANCESTOR_IDS;
        case ExpressionEvaluator.NXQL_ECM_PATH:
            return KEY_PATH_INTERNAL;
        case ExpressionEvaluator.NXQL_ECM_READ_ACL:
            return KEY_READ_ACL;
        }
        throw new RuntimeException("Unknown property: " + name);
    }

    public static String convToNXQL(String name) {
        switch (name) {
        case KEY_ID:
            return NXQL.ECM_UUID;
        case KEY_NAME:
            return NXQL.ECM_NAME;
        case KEY_POS:
            return NXQL.ECM_POS;
        case KEY_PARENT_ID:
            return NXQL.ECM_PARENTID;
        case KEY_MIXIN_TYPES:
            return NXQL.ECM_MIXINTYPE;
        case KEY_PRIMARY_TYPE:
            return NXQL.ECM_PRIMARYTYPE;
        case KEY_IS_PROXY:
            return NXQL.ECM_ISPROXY;
        case KEY_IS_VERSION:
            return NXQL.ECM_ISVERSION;
        case KEY_LIFECYCLE_STATE:
            return NXQL.ECM_LIFECYCLESTATE;
        case KEY_LIFECYCLE_POLICY:
            return null;
        case KEY_LOCK_OWNER:
            return NXQL.ECM_LOCK_OWNER;
        case KEY_LOCK_CREATED:
            return NXQL.ECM_LOCK_CREATED;
        case KEY_PROXY_TARGET_ID:
            return NXQL.ECM_PROXY_TARGETID;
        case KEY_PROXY_VERSION_SERIES_ID:
            return NXQL.ECM_PROXY_VERSIONABLEID;
        case KEY_IS_CHECKED_IN:
            return NXQL.ECM_ISCHECKEDIN;
        case KEY_IS_LATEST_VERSION:
            return NXQL.ECM_ISLATESTVERSION;
        case KEY_IS_LATEST_MAJOR_VERSION:
            return NXQL.ECM_ISLATESTMAJORVERSION;
        case KEY_VERSION_LABEL:
            return NXQL.ECM_VERSIONLABEL;
        case KEY_VERSION_CREATED:
            return NXQL.ECM_VERSIONCREATED;
        case KEY_VERSION_DESCRIPTION:
            return NXQL.ECM_VERSIONDESCRIPTION;
        case KEY_VERSION_SERIES_ID:
            return NXQL.ECM_VERSION_VERSIONABLEID;
        case KEY_MAJOR_VERSION:
            return "major_version"; // TODO XXX constant
        case KEY_MINOR_VERSION:
            return "minor_version";
        case KEY_ACP:
            return null;
        case KEY_ANCESTOR_IDS:
            return null;
        case KEY_BASE_VERSION_ID:
            return null;
        case KEY_READ_ACL:
            return null;
        }
        throw new RuntimeException("Unknown property: " + name);
    }

    public static boolean isArray(String name) {
        switch (name) {
        case KEY_MIXIN_TYPES:
        case KEY_ANCESTOR_IDS:
        case KEY_PROXY_IDS:
            return true;
        }
        return false;
    }

    public static boolean isBoolean(String name) {
        switch (name) {
        case KEY_IS_VERSION:
        case KEY_IS_CHECKED_IN:
        case KEY_IS_LATEST_VERSION:
        case KEY_IS_LATEST_MAJOR_VERSION:
        case KEY_IS_PROXY:
            return true;
        }
        return false;
    }

}
