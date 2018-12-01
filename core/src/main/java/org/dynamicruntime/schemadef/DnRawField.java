package org.dynamicruntime.schemadef;

import org.dynamicruntime.exception.DnException;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;


import java.util.Map;

/** Convenience class for building up a Map of data holding the definition data for a DnField. */
@SuppressWarnings("WeakerAccess")
public class DnRawField {
    public final String name;
    public final Map<String,Object> data;

    public DnRawField(String name, Map<String,Object> data) {
        this.name = name;
        this.data = data;
    }

    public static DnRawField mkRawField(Map<String,Object> data) throws DnException {
        String name = getReqStr(data, DN_NAME);
        return new DnRawField(name, data);
    }

    public static DnRawField mkField(String name, String label, String description) throws DnException {
        return mkRawField(mMap(DN_NAME, name, DN_LABEL, label, DN_DESCRIPTION, description));
    }

    public static DnRawField mkReqField(String name, String label, String description) throws DnException {
        return mkField(name, label, description).setOption(DN_REQUIRED, true);
    }

    public DnRawField setTypeRef(String typeRef) {
        data.put(DN_TYPE_REF, typeRef);
        return this;
    }

    public DnRawField setTypeDef(DnRawType rawType) {
        data.put(DN_TYPE_DEF, rawType.model);
        return this;
    }

    public DnRawField setOption(String optionName, Object optionValue) {
        data.put(optionName, optionValue);
        return this;
    }

    /** Only replaces the options specified. */
    public DnRawField setOptions(Map<String,Object> options) {
        data.putAll(options);
        return this;
    }

    /** Called when raw field is added to raw type. */
    public DnRawField finish() {
        if (!data.containsKey(DN_TYPE_DEF) && !data.containsKey(DN_TYPE_REF)) {
            data.put(DN_TYPE_REF, DN_STRING);
        }
        return this;
    }
}
