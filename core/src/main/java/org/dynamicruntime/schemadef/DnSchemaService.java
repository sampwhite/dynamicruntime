package org.dynamicruntime.schemadef;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.startup.ServiceInitializer;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.LogSchema.*;

@SuppressWarnings("WeakerAccess")
public class DnSchemaService implements ServiceInitializer {
    public static final String DN_SCHEMA_SERVICE = DnSchemaService.class.getSimpleName();
    public DnRawSchemaStore rawSchemaStore;
    public AtomicReference<DnSchemaStore> schemaStore = new AtomicReference<>();
    public boolean isInit = false;

    public static DnSchemaService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(DN_SCHEMA_SERVICE);
        return (obj instanceof DnSchemaService) ? (DnSchemaService)obj : null;
    }

    @Override
    public String getServiceName() {
        return DN_SCHEMA_SERVICE;
    }

    @Override
    public void checkInit(DnCxt cxt) throws DnException {
        if (isInit) {
            return;
        }

        rawSchemaStore = DnRawSchemaStore.get(cxt);
        createSchemaStore(cxt);

        isInit = true;
    }

    public DnSchemaStore getSchemaStore() {
        return schemaStore.get();
    }

    public void createSchemaStore(DnCxt cxt) throws DnException {
        log.debug(cxt, "Turning raw schema into read only Java objects.");
        synchronized (this) {
            // Eventually we will extract DnEndpoints and DnTables as well.
            Map<String,DnType> dnTypes = mMapT();
            for (DnRawType rawType : rawSchemaStore.rawTypes.values()) {
                DnType dnType = DnType.extract(rawType.model, rawSchemaStore.rawTypes);
                dnTypes.put(dnType.name, dnType);
            }
            schemaStore.set(new DnSchemaStore(dnTypes));
        }
    }
}
