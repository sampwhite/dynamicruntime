package org.dynamicruntime.sql;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class SqlColumnAliases {
    /** Use a column name, get the value it should become in a  Map. This should be treated as an
     * immutable map. */
    public final Map<String,String> colNameToFieldName;
    /** Take a named value from a map and give it a column name. This should be treated as an immutable map. */
    public final Map<String,String> fieldNameToColName;

    /** Feeder for creating new versions of this object. This should not be touched by consumers of
     * the field maps. */
    public final Map<String,String> newFieldNameToColNameFeeder = new ConcurrentHashMap<>();

    public SqlColumnAliases(Map<String,String> colNameToFieldName, Map<String,String> fieldNameToColName) {
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
        return new SqlColumnAliases(cf, fc);
    }
}
