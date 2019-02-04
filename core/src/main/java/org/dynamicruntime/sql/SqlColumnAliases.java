package org.dynamicruntime.sql;

import org.dynamicruntime.schemadef.DnField;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class SqlColumnAliases {
    /** Convert column names to lower case. */
    public final boolean toLowerCaseColumns;
    /** Fields whose names are reserved for particular common activities. Example entries are
     * *limit*, *from*, and *until*. */
    public final Map<String, DnField> reservedFields;
    /** Use a column name, get the value it should become in a map. This should be treated as an
     * immutable map. */
    public final Map<String,String> colNameToFieldName;
    /** Take a named value from a map and give it a column name. This should be treated as an immutable map. */
    public final Map<String,String> fieldNameToColName;

    /** Feeder for creating new versions of this object. This should not be touched by consumers of
     * the field maps. */
    public final Map<String,String> newFieldNameToColNameFeeder = new ConcurrentHashMap<>();

    public SqlColumnAliases(boolean toLowerCaseColumns, Map<String,DnField> reservedFields,
            Map<String,String> colNameToFieldName,
            Map<String,String> fieldNameToColName) {
        this.reservedFields = reservedFields;
        this.toLowerCaseColumns = toLowerCaseColumns;
        this.colNameToFieldName = colNameToFieldName;
        this.fieldNameToColName = fieldNameToColName;
    }

    public SqlColumnAliases getUpdated() {
        var cf = cloneMap(colNameToFieldName);
        var fc = cloneMap(fieldNameToColName);
        for (var entry : newFieldNameToColNameFeeder.entrySet()) {
            var k = entry.getKey();
            var v = entry.getValue();
            cf.put(v, k);
            fc.put(k, v);
        }
        return new SqlColumnAliases(toLowerCaseColumns, reservedFields, cf, fc);
    }

    public String getColumnName(String fieldName) {
        String c = fieldNameToColName.get(fieldName);
        return c != null ? c : fieldName;
    }

    public String getFieldName(String columnName) {
        String c = (toLowerCaseColumns) ? columnName.toLowerCase() : columnName;
        String f = colNameToFieldName.get(c);
        return f != null ? f : c;
    }

}
