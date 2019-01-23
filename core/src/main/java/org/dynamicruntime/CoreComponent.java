package org.dynamicruntime;

import org.dynamicruntime.content.DnContentService;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.Priority;
import org.dynamicruntime.endpoint.NodeEndpoints;
import org.dynamicruntime.endpoint.SchemaEndpoints;
import org.dynamicruntime.node.DnCoreNodeService;
import org.dynamicruntime.schemadata.NodeCoreSchema;
import org.dynamicruntime.schemadata.SchemaForSchema;
import org.dynamicruntime.schemadef.DnRawSchemaStore;
import org.dynamicruntime.schemadef.DnSchemaService;
import org.dynamicruntime.servlet.DnRequestService;
import org.dynamicruntime.sql.topic.SqlTopicService;
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
    public String getConfigFileName() {
        return "dnCoreConfig.yaml";
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
        schemaStore.addPackage(NodeCoreSchema.getPackage());
        schemaStore.addFunctions(NodeEndpoints.getFunctions());

        schemaStore.addPackage(SchemaForSchema.getPackage());
        schemaStore.addFunctions(SchemaEndpoints.getFunctions());
    }

    @Override
    public Collection<Class> getStartupInitializers(DnCxt cxt) {
        return mList(DnSchemaService.class, SqlTopicService.class);
    }

    @Override
    public Collection<Class> getServiceInitializers(DnCxt cxt) {
        return mList(DnRequestService.class, DnContentService.class, DnCoreNodeService.class);
    }

    @Override
    public int loadPriority() {
        return Priority.STANDARD - 1;
    }

}
