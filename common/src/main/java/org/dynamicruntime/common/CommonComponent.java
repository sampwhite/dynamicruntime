package org.dynamicruntime.common;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.schemadef.DnRawSchemaStore;
import org.dynamicruntime.startup.ComponentDefinition;

import java.util.Collection;

@SuppressWarnings({"WeakerAccess"})
public class CommonComponent implements ComponentDefinition {
    public static final String COMMON_COMPONENT = "common";

    @Override
    public String getComponentName() {
        return COMMON_COMPONENT;
    }

    @Override
    public String getConfigFileName() {
        return "dnCommonConfig.yaml";
    }

    /** Eventually these may allow configuration to drive whether this returns true or not, same for *isActive*. */
    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public boolean isActive() {
        return true;
    }
    @Override
    public void addSchema(DnCxt cxt, DnRawSchemaStore schemaStore) {

    }
    @Override
    public Collection<Class> getStartupInitializers(DnCxt cxt) {
        return null;
    }
    @Override
    public Collection<Class> getServiceInitializers(DnCxt cxt) {
        return null;
    }
}
