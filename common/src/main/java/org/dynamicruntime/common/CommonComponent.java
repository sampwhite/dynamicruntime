package org.dynamicruntime.common;

import org.dynamicruntime.common.mail.DnMailEndpoints;
import org.dynamicruntime.common.mail.DnMailSchemaDefData;
import org.dynamicruntime.common.node.DnNodeSchemaDefData;
import org.dynamicruntime.common.node.DnNodeService;
import org.dynamicruntime.common.user.UserEndpoints;
import org.dynamicruntime.common.user.AuthUserEndpoints;
import org.dynamicruntime.common.user.UserSchemaDefData;
import org.dynamicruntime.common.user.UserService;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.schemadef.DnRawSchemaStore;
import org.dynamicruntime.startup.ComponentDefinition;

import java.util.Collection;

import static org.dynamicruntime.util.DnCollectionUtil.mList;

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
        schemaStore.addPackage(UserSchemaDefData.getPackage());
        schemaStore.addPackage(DnNodeSchemaDefData.getPackage());
        schemaStore.addFunctions(AuthUserEndpoints.getFunctions());
        schemaStore.addFunctions(UserEndpoints.getFunctions());
        schemaStore.addPackage(DnMailSchemaDefData.getPackage());
        schemaStore.addFunctions(DnMailEndpoints.getFunctions());

    }
    @Override
    public Collection<Class> getStartupInitializers(DnCxt cxt) {
        return null;
    }
    @Override
    public Collection<Class> getServiceInitializers(DnCxt cxt) {
        return mList(UserService.class, DnNodeService.class);
    }
}
