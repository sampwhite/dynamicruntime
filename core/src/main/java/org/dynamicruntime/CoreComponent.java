package org.dynamicruntime;

import org.dynamicruntime.content.DnContentService;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.endpoint.NodeEndpoints;
import org.dynamicruntime.endpoint.SchemaEndpoints;
import org.dynamicruntime.node.DnNodeService;
import org.dynamicruntime.schemadata.NodeSchema;
import org.dynamicruntime.schemadata.SchemaForSchema;
import org.dynamicruntime.schemadef.DnRawSchemaStore;
import org.dynamicruntime.schemadef.DnSchemaService;
import org.dynamicruntime.servlet.DnRequestService;
import org.dynamicruntime.startup.ComponentDefinition;

import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.util.Collection;

@SuppressWarnings("WeakerAccess")
public class CoreComponent implements ComponentDefinition {
    public static final String CORE_COMPONENT = "core";

    @Override
    public String getComponentName() {
        return CORE_COMPONENT;
    }

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
        schemaStore.addFunctions(NodeEndpoints.getFunctions());
        schemaStore.addPackage(NodeSchema.getPackage());
        schemaStore.addPackage(SchemaForSchema.getPackage());
        schemaStore.addFunctions(SchemaEndpoints.getFunctions());
    }

    @Override
    public Collection<Class> getStartupInitializers(DnCxt cxt) {
        return mList(DnSchemaService.class);
    }

    @Override
    public Collection<Class> getServiceInitializers(DnCxt cxt) {
        return mList(DnRequestService.class, DnContentService.class, DnNodeService.class);
    }
}
