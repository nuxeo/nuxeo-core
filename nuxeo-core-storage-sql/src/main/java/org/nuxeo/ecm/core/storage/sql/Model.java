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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.Type;

/**
 * The {@link Model} is the link between high-level types and SQL-level
 * objects (entity tables, collections). It defines all policies relating to the
 * choice of structure (what schema are grouped together in for optimization)
 * and names in the SQL database (table names, column names), and to what entity
 * names (type name, field name) they correspond.
 * <p>
 * A Nuxeo schema or type is mapped to a SQL-level table. Several types can be
 * aggregated in the same table. In theory, a type could even be split into
 * different tables.
 *
 * @author Florent Guillaume
 */
public class Model {

    private static final Log log = LogFactory.getLog(Model.class);

    public static final String ROOT_TYPE = "Root";

    public static final String REPOINFO_TABLE_NAME = "repositoryinfo";

    public static final String REPOINFO_ROOTID_KEY = "rootid";

    public static final String MAIN_KEY = "id";

    public static final String MAIN_PRIMARY_TYPE_PROP = "ecm:primaryType";

    public static final String MAIN_TABLE_NAME = "types";

    public static final String MAIN_PRIMARY_TYPE_KEY = "primaryType";

    public static final String HIER_TABLE_NAME = "hierarchy";

    public static final String HIER_PARENT_KEY = "parent";

    public static final String HIER_CHILD_NAME_KEY = "name";

    public static final String HIER_CHILD_POS_KEY = "pos";

    public static final String COLL_TABLE_POS_KEY = "pos";

    public static final String COLL_TABLE_VALUE_KEY = "item";

    /** Maps table name to a map of properties to their basic type. */
    public Map<String, Map<String, PropertyType>> fragmentsKeysType;

    /** Maps collection table names to their type. */
    public Map<String, PropertyType> collectionTables;

    /** Maps property name to fragment name. */
    private Map<String, String> propertyFragment;

    /** Maps property name to fragment key (single-valued). */
    private Map<String, String> propertyFragmentKey;

    /** Maps of properties to their basic type. */
    public Map<String, PropertyType> propertyType;

    public Model(SchemaManager schemaManager) {
        propertyType = new HashMap<String, PropertyType>();
        fragmentsKeysType = new HashMap<String, Map<String, PropertyType>>();
        collectionTables = new HashMap<String, PropertyType>();
        propertyFragment = new HashMap<String, String>();
        propertyFragmentKey = new HashMap<String, String>();

        initMainModel();
        initModels(schemaManager);
    }

    public PropertyType getPropertyType(String propertyName) {
        return propertyType.get(propertyName);
    }

    public String getFragmentName(String propertyName) {
        return propertyFragment.get(propertyName);
    }

    public String getFragmentKey(String propertyName) {
        return propertyFragmentKey.get(propertyName);
    }

    /**
     * Creates all the models.
     */
    private void initModels(SchemaManager schemaManager) {

        for (DocumentType documentType : schemaManager.getDocumentTypes()) {
            for (Schema schema : documentType.getSchemas()) {
                initTypeModel(schema);
            }
        }

    }

    /**
     * Special model for the main table (the one containing the primary type
     * information).
     */
    private void initMainModel() {
        String tableName = MAIN_TABLE_NAME;
        String propertyName = MAIN_PRIMARY_TYPE_PROP;
        PropertyType type = PropertyType.STRING;
        propertyType.put(propertyName, type);
        propertyFragment.put(propertyName, tableName);
        propertyFragmentKey.put(propertyName, MAIN_PRIMARY_TYPE_KEY);

        Map<String, PropertyType> fragmentKeysType = fragmentsKeysType.get(tableName);
        if (fragmentKeysType == null) {
            fragmentKeysType = new HashMap<String, PropertyType>();
            fragmentsKeysType.put(tableName, fragmentKeysType);
        }
        fragmentKeysType.put(MAIN_PRIMARY_TYPE_KEY, type);
    }

    /**
     * Creates the model for one schema or complex type.
     */
    private void initTypeModel(ComplexType complexType) {
        String typeName = complexType.getName();

        log.debug("Making model for type " + typeName);

        for (Field field : complexType.getFields()) {
            Type fieldType = field.getType();
            if (fieldType.isComplexType()) {
                initTypeModel((ComplexType) fieldType);
            } else {
                String propertyName = field.getName().getPrefixedName();
                if (fieldType.isListType()) {
                    Type listFieldType = ((ListType) fieldType).getFieldType();
                    if (listFieldType.isSimpleType()) {
                        /*
                         * Array: use a collection table.
                         */
                        PropertyType type = PropertyType.fromFieldType(
                                listFieldType, true);
                        propertyType.put(propertyName, type);

                        // XXX collection table name
                        String tableName = String.valueOf(type);
                        propertyFragment.put(propertyName, tableName);
                        collectionTables.put(tableName, type);
                    } else {
                        /*
                         * List.
                         */
                        // TODO list of complex types
                    }
                } else {
                    /*
                     * Primitive type.
                     */
                    PropertyType type = PropertyType.fromFieldType(fieldType,
                            false);

                    propertyType.put(propertyName, type);
                    String tableName = typeName; // TODO use policy config
                    propertyFragment.put(propertyName, tableName);
                    String key = field.getName().getLocalName();
                    propertyFragmentKey.put(propertyName, key);

                    Map<String, PropertyType> fragmentKeysType = fragmentsKeysType.get(tableName);
                    if (fragmentKeysType == null) {
                        fragmentKeysType = new HashMap<String, PropertyType>();
                        fragmentsKeysType.put(tableName, fragmentKeysType);
                    }
                    fragmentKeysType.put(key, type);
                }
            }
        }
    }

}
