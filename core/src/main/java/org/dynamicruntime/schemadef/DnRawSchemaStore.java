package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Holds raw schema. This object requires synchronization on it before its contents can be either
 * accessed or modified (except during startup code). */
@SuppressWarnings("WeakerAccess")
public class DnRawSchemaStore {
    public static final String DN_RAW_SCHEMA_STORE = DnRawSchemaStore.class.getSimpleName();

    /** The DnRawSchemaStore is populated directly by the instance initializer early on. */
    public static DnRawSchemaStore get(DnCxt cxt) {
        Object obj = cxt.instanceConfig.get(DN_RAW_SCHEMA_STORE);
        return (obj instanceof DnRawSchemaStore) ? (DnRawSchemaStore)obj : null;
    }

    public Map<String,DnRawSchemaPackage> schemaPackages = new LinkedHashMap<>();

    public Map<String,DnRawType> rawTypes = mMapT();

    public void addPackage(DnRawSchemaPackage pckg) {
        schemaPackages.put(pckg.packageName, pckg);

        for (var rawType : pckg.rawTypes) {
            var clonedRawType = rawType.cloneType(pckg.namespace);
            rawTypes.put(clonedRawType.name, clonedRawType);
        }
    }

}
