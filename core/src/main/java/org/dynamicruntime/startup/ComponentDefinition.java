package org.dynamicruntime.startup;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.schemadef.DnRawSchemaStore;

import java.util.ArrayList;
import java.util.Collection;

public interface ComponentDefinition {
    String getComponentName();
    boolean isLoaded();
    boolean isActive();

    void addSchemaPackages(DnCxt cxt, DnRawSchemaStore schemaStore);
    default Collection<Class> getStartupInitializers(DnCxt cxt) {
        return new ArrayList<>();
    }
    Collection<Class> getServiceInitializers(DnCxt cxt);
}
