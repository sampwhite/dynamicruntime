package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;

import java.util.Collections;
import java.util.Map;

/** Read only store of DnType objects designed for consumption. */
@SuppressWarnings("WeakerAccess")
public class DnSchemaStore {
    /** Types by namespaced names. */
    public final Map<String,DnType> types;
    /** Endpoints by path. Pulled from *types*.*/
    public final Map<String,DnEndpoint> endpoints;

    public DnSchemaStore(Map<String,DnType> types, Map<String,DnEndpoint> endpoints) {
        this.types = types;
        this.endpoints = endpoints;
    }

    public static DnSchemaStore get(DnCxt cxt) {
        DnSchemaService schemaService = DnSchemaService.get(cxt);
        return schemaService != null ? schemaService.getSchemaStore() : null;
    }

    public DnType getType(String typeName) {
        return types.get(typeName);
    }
}
