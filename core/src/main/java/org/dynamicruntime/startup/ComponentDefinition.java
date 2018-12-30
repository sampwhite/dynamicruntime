package org.dynamicruntime.startup;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.Priority;
import org.dynamicruntime.schemadef.DnRawSchemaStore;

import java.util.Collection;

public interface ComponentDefinition {
    String getComponentName();
    default String getConfigFileName() {
        return null;
    }
    boolean isLoaded();
    boolean isActive();

    void addSchema(DnCxt cxt, DnRawSchemaStore schemaStore);
    default Collection<Class> getStartupInitializers(DnCxt cxt) {
        return null;
    }
    Collection<Class> getServiceInitializers(DnCxt cxt);
    default int loadPriority() {
        return Priority.STANDARD;
    }
}
