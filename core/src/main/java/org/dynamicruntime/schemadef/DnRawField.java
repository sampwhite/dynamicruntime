package org.dynamicruntime.schemadef;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;


import java.util.Map;

/** Convenience class for building up a Map of data holding the definition data for a DnField. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DnRawField {
    public final String name;
    public final Map<String,Object> data;

    public DnRawField(String name, Map<String,Object> data) {
        this.name = name;
        this.data = data;
    }

    public static DnRawField mkRawField(Map<String,Object> data) {
        String name = getOptStr(data, DN_NAME);
        if (name == null) {
            return null;
        }
        return new DnRawField(name, data);
    }

    public static DnRawField mkField(String name, String label, String description) {
        return mkRawField(mMap(DN_NAME, name, DN_LABEL, label, DN_DESCRIPTION, description));
    }

    public static DnRawField mkReqField(String name, String label, String description) {
        return mkField(name, label, description).setRequired(true);
    }

    public static DnRawField mkIntField(String name, String label, String description) {
        return mkField(name, label, description).setTypeRef(DNT_INTEGER);
    }

    public static DnRawField mkReqIntField(String name, String label, String description) {
        return mkField(name, label, description).setTypeRef(DNT_INTEGER).setRequired(true);
    }

    public static DnRawField mkBoolField(String name, String label, String description) {
        return mkField(name, label, description).setTypeRef(DNT_BOOLEAN);
    }

    public static DnRawField mkReqBoolField(String name, String label, String description) {
        return mkField(name, label, description).setTypeRef(DNT_BOOLEAN).setRequired(true);
    }

    public static DnRawField mkDateField(String name, String label, String description) {
        return mkField(name, label, description).setTypeRef(DNT_DATE);
    }

    public static DnRawField mkReqDateField(String name, String label, String description) {
        return mkField(name, label, description).setTypeRef(DNT_DATE).setRequired(true);
    }

    public DnRawField setTypeRef(String typeRef) {
        data.put(DN_TYPE_REF, typeRef);
        return this;
    }

    public DnRawField setTypeDef(DnRawType rawType) {
        rawType.finish();
        data.put(DN_TYPE_DEF, rawType.model);
        return this;
    }

    public DnRawField setRequired(boolean required) {
        data.put(DN_REQUIRED, required);
        return this;
    }

    public DnRawField setRank(int rank) {
        data.put(DN_SORT_RANK, rank);
        return this;
    }

    public DnRawField setAttribute(String attName, Object attValue) {
        data.put(attName, attValue);
        return this;
    }

    /** Only replaces the attributes specified. */
    public DnRawField setAttributes(Map<String,Object> attributes) {
        data.putAll(attributes);
        return this;
    }

    /** Called when raw field is added to raw type. */
    public DnRawField finish() {
        if (!data.containsKey(DN_TYPE_DEF) && !data.containsKey(DN_TYPE_REF)) {
            data.put(DN_TYPE_REF, DNT_STRING);
        }
        return this;
    }
}
