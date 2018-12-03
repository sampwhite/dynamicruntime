package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Holds raw schema. This object requires synchronization on it before its contents can be either
 * accessed or modified (except during startup code). Note that this object is created before
 * any of the initialization of {@link org.dynamicruntime.startup.ServiceInitializer} methods are called.
 * If a ServiceInitializer wishes to contribute additional items to the store, they should do it in their
 * *onCreate* method before the *DnSchemaService* builds the *DnSchemaStore*. */
@SuppressWarnings("WeakerAccess")
public class DnRawSchemaStore {
    public static final String DN_RAW_SCHEMA_STORE = DnRawSchemaStore.class.getSimpleName();

    /** The DnRawSchemaStore is populated directly by the instance initializer early on. */
    public static DnRawSchemaStore get(DnCxt cxt) {
        Object obj = cxt.instanceConfig.get(DN_RAW_SCHEMA_STORE);
        return (obj instanceof DnRawSchemaStore) ? (DnRawSchemaStore)obj : null;
    }

    /** Functions to execute endpoint requests. */
    public Map<String, DnEndpointFunction> functions = mMapT();

    /** Objects that create more complex raw types from simpler inputs. The builders should be registered early. */
    public Map<String,DnBuilder> builders = mMapT();

    public Map<String,DnRawSchemaPackage> schemaPackages = new LinkedHashMap<>();

    public Map<String,DnRawType> rawTypes = mMapT();

    public void addPackage(DnRawSchemaPackage pckg) {
        schemaPackages.put(pckg.packageName, pckg);

        for (var rawType : pckg.rawTypes) {
            var clonedRawType = rawType.cloneType(pckg.namespace);
            rawTypes.put(clonedRawType.name, clonedRawType);
        }
    }

    public void addFunctions(List<DnEndpointFunction> endpointFunctions) {
        for (var endpointFunction : endpointFunctions) {
            functions.put(endpointFunction.name, endpointFunction);
        }
    }

    public void addFunction(String functionName, DnEndpointFunctionInterface functionImpl) {
        functions.put(functionName, new DnEndpointFunction(functionName, functionImpl));
    }

}
