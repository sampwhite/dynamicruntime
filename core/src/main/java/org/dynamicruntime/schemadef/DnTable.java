package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.util.List;
import java.util.Map;


@SuppressWarnings("WeakerAccess")
public class DnTable {
    public static class Index {
        /** Name of index. Provided only if code wants to use index later to help build queries. */
        public final String name;
        /** The field declarations in the index. The declarations can include additional information about sort order
         * using a space separator after the field name. */
        public final List<String> fieldDeclarations;
        /** The field name portions of the declaration entries. This is the columns without the extra info following
         * the field name. */
        public final List<String> fieldNames;
        /** Extended properties of the index, including things like whether it has a uniqueness constraint or not. */
        public final Map<String,Object> indexProperties;

        public Index(String name, List<String> fieldDeclarations, Map<String,Object> indexProperties) {
            this.name = name;
            this.fieldDeclarations = fieldDeclarations;
            this.fieldNames = nMapSimple(fieldDeclarations, (s ->
                    StrUtil.getToNextIndex(s, 0, " ")));
            this.indexProperties = indexProperties;
        }

        public static Index extract(Object obj) throws DnException {
            if (obj instanceof List) {
                return new Index(null, nMapSimple((List<?>)obj, Object::toString), mMap());
            } else if (obj instanceof Map) {
                Map<String,Object> map = toOptMap(obj);
                String name = getOptStr(map, DN_NAME);
                Object fieldsObj = map.get(TB_INDEX_FIELDS);
                if (!(fieldsObj instanceof List)) {
                    throw DnException.mkConv("Could not extract fields from index for object " +
                            fmtObject(obj) + ".");
                }
                Map<String,Object> props = getMapDefaultEmpty(map, TB_INDEX_PROPS);
                List<String> fields = nMapSimple((List<?>)fieldsObj,Object::toString);
                return new Index(name, fields, props);
            } else {
                throw DnException.mkConv("Could not extract index from " + fmtObject(obj) + ".");
            }
        }
    }

    public final String tableName;
    public final List<DnField> columns;
    public final Map<String,DnField> columnsByName;
    public final Index primaryKey;
    public final List<Index> indexes;
    public final boolean firstColIsCounter;
    // Other properties from the schema.
    public final Map<String,Object> data;

    public DnTable(String tableName, List<DnField> columns, Index primaryKey, List<Index> indexes,
            Map<String,Object> data) {
        this.tableName = tableName;
        this.columns = columns;
        this.columnsByName = mMapT();
        for (var col : columns) {
            columnsByName.put(col.name, col);
        }
        this.primaryKey = primaryKey;
        this.indexes = indexes;
        this.data = data;
        boolean hasCounter = false;
        if (columns.size() > 0) {
            DnField firstCol = columns.get(0);
            hasCounter = getBoolWithDefault(firstCol.data, DN_IS_AUTO_INCREMENTING, false);
        }
        this.firstColIsCounter = hasCounter;
    }

    public static DnTable extract(DnType dnType) throws DnException {
        var data = dnType.model;
        String tableName = getReqStr(data, TB_NAME);
        Object indexesObj = data.get(TB_INDEXES);
        Object primaryKeyObj = data.get(TB_PRIMARY_KEY);
        if (!(primaryKeyObj instanceof List) && !(primaryKeyObj instanceof Map)) {
            throw DnException.mkConv(String.format("Primary key missing or has wrong data for table %s.", tableName));
        }
        Index primaryKey = Index.extract(primaryKeyObj);
        List<Index> indexes = mList();
        if (indexesObj instanceof List) {
            for (Object o : (List)indexesObj) {
                indexes.add(Index.extract(o));
            }
        }
        return new DnTable(tableName, dnType.fields, primaryKey, indexes, data);
    }
}
