package org.dynamicruntime.simulation;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnRawSchemaPackage;
import org.dynamicruntime.schemadef.DnRawSchemaStore;
import org.dynamicruntime.schemadef.DnSchemaService;
import org.dynamicruntime.startup.ComponentDefinition;
import org.dynamicruntime.util.DnCollectionUtil;

import java.util.Collection;

/** Code use for tests and simulations. In general in this application test *fixtures* are
 * turn into simulation code so they can be available at runtime of the app when it is run
 * in special simulation modes. It also makes it simpler to share between projects. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class TestComponent implements ComponentDefinition {
    public DnRawSchemaPackage schemaPackage;

    public TestComponent(DnRawSchemaPackage schemaPackage) {
        this.schemaPackage = schemaPackage;
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
        schemaStore.addPackage(schemaPackage);
        schemaStore.addFunction("testEndpoint", this::testEndpoint);
    }

    @Override
    public Collection<Class> getStartupInitializers(DnCxt cxt) {
        return DnCollectionUtil.mList(DnSchemaService.class);
    }

    @Override
    public Collection<Class> getServiceInitializers(DnCxt cxt) {
        return DnCollectionUtil.mList();
    }

    public void testEndpoint(DnRequestCxt requestCxt) {

    }
}
