package org.dynamicruntime.simulation;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnRawSchemaPackage;
import org.dynamicruntime.schemadef.DnRawSchemaStore;
import org.dynamicruntime.schemadef.DnSchemaService;
import org.dynamicruntime.sql.topic.SqlTopicService;
import org.dynamicruntime.startup.ComponentDefinition;

import static org.dynamicruntime.util.DnCollectionUtil.*;


import java.util.Collection;
import java.util.List;

/** Code use for tests and simulations. In general in this application test *fixtures* are
 * turn into simulation code so they can be available at runtime of the app when it is run
 * in special simulation modes. It also makes it simpler to share between projects. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TestComponent implements ComponentDefinition {
    public List<DnRawSchemaPackage> schemaPackages;
    public List<Class> serviceInitializers;

    public TestComponent(DnRawSchemaPackage schemaPackage) {
        this.schemaPackages = mList(schemaPackage);
        this.serviceInitializers = mList();
    }

    public TestComponent(List<DnRawSchemaPackage> schemaPackages, List<Class> serviceInitializers) {
        this.schemaPackages = schemaPackages;
        this.serviceInitializers = serviceInitializers;
    }

    @Override
    public String getComponentName() {
        return "TestComponent";
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
        for (var schemaPackage : schemaPackages) {
            schemaStore.addPackage(schemaPackage);
        }
       schemaStore.addFunction("testEndpoint", this::testEndpoint);
    }

    @Override
    public Collection<Class> getStartupInitializers(DnCxt cxt) {
        return mList(DnSchemaService.class, SqlTopicService.class);
    }

    @Override
    public Collection<Class> getServiceInitializers(DnCxt cxt) {
        return serviceInitializers;
    }

    public void testEndpoint(DnRequestCxt requestCxt) {

    }
}
