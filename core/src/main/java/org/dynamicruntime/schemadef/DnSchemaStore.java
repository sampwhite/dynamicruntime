package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;

import java.util.Collections;
import java.util.Map;

/** Read only store of DnType objects designed for consumption. */
@SuppressWarnings("WeakerAccess")
public class DnSchemaStore {
    public final Map<String,DnType> types;

    public DnSchemaStore(Map<String,DnType> types) {
        this.types = Collections.unmodifiableMap(types);
    }

    public static DnSchemaStore get(DnCxt cxt) {
        DnSchemaService schemaService = DnSchemaService.get(cxt);
        return schemaService != null ? schemaService.getSchemaStore() : null;
    }

    public DnType getType(String typeName) {
        return types.get(typeName);
    }
}
