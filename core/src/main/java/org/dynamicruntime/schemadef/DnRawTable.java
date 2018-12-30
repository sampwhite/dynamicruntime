package org.dynamicruntime.schemadef;

import java.util.List;
import java.util.Map;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

/** Construction mechanism for creating table definitions. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DnRawTable implements DnRawTypeInterface {
    public final String tableName;
    public final Map<String,Object> tbModel;
    public final List<DnRawField> fields;

    public DnRawTable(String tableName, Map<String,Object> tbModel, List<DnRawField> fields) {
         this.tableName = tableName;
         this.tbModel = tbModel;
         this.fields = fields;
    }

    @Override
    public DnRawType getRawType() {
        String name = getOptStr(tbModel, DN_NAME);
        if (name == null) {
            name = tableName + "Table";
            tbModel.put(DN_NAME, name);
        }
        tbModel.put(TB_NAME, tableName);
        tbModel.put(DN_BUILDER, TB_TABLE);

        var rawType =  new DnRawType(name, tbModel);
        rawType.addFields(fields);
        return rawType;
    }

    public static DnRawTable mkTable(String tableName, String description, List<DnRawField> fields,
            List<String> primaryKey) {
        var model = mMap(DN_DESCRIPTION, description, TB_PRIMARY_KEY, primaryKey);
        return new DnRawTable(tableName, model, fields);
    }

    public static DnRawTable mkStdTable(String tableName, String description, List<DnRawField> fields,
            List<String> primaryKey) {
        var model = mMap(DN_DESCRIPTION, description, TB_PRIMARY_KEY, primaryKey,
                TB_HAS_ROW_DATES, true);
        return new DnRawTable(tableName, model, fields);
    }

    public static DnRawTable mkStdUserTable(String tableName, String description, List<DnRawField> fields,
            List<String> primaryKey) {
        var model = mMap(DN_DESCRIPTION, description, TB_PRIMARY_KEY, primaryKey,
                TB_HAS_ROW_DATES, true, TB_IS_USER_DATA, true);
        return new DnRawTable(tableName, model, fields);
    }

    public static DnRawTable mkStdUserTopLevelTable(String tableName, String description, List<DnRawField> fields,
            List<String> primaryKey) {
        var model = mMap(DN_DESCRIPTION, description, TB_PRIMARY_KEY, primaryKey,
                TB_HAS_ROW_DATES, true, TB_IS_USER_DATA, true, TB_IS_TOP_LEVEL, true);
        return new DnRawTable(tableName, model, fields);

    }
    public DnRawTable setCounterField(String fieldName) {
        tbModel.put(TB_COUNTER_FIELD, fieldName);
        return this;
    };

    public DnRawTable setAttribute(String key, Object val) {
        tbModel.put(key, val);
        return this;
    }

    public DnRawTable setTopLevel() {
        return setAttribute(TB_IS_TOP_LEVEL, true);
    }

    public DnRawTable setSimpleIndexes(List<List<String>> indexes) {
        tbModel.put(TB_INDEXES, indexes);
        return this;
    }

    public DnRawTable setComplexIndexes(List<Object> indexes) {
        tbModel.put(TB_INDEXES, indexes);
        return this;
    }

    public static Map<String,Object> mkComplexIndex(String name, List<String> fields, Map<String,Object> props) {
        return mMap(DN_NAME, name, TBI_INDEX_FIELDS, fields, TBI_INDEX_PROPS, props);
    }
}
