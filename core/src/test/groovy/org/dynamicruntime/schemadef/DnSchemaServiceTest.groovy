package org.dynamicruntime.schemadef

import org.dynamicruntime.context.DnCxt
import org.dynamicruntime.request.DnRequestCxt
import org.dynamicruntime.startup.ComponentDefinition
import org.dynamicruntime.startup.InstanceRegistry
import org.dynamicruntime.startup.LogStartup
import spock.lang.Specification

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*


class DnSchemaServiceTest extends Specification {
    // Declaring this class seems to be adding 600ms to test time, for reasons that are not clear.
    class TestComponent implements ComponentDefinition {
        DnRawSchemaPackage schemaPackage

        TestComponent(DnRawSchemaPackage schemaPackage) {
            this.schemaPackage = schemaPackage
        }

        @Override
        String getComponentName() {
            return "TestComponent"
        }

        @Override
        boolean isLoaded() {
            return true
        }

        @Override
        boolean isActive() {
            return true
        }

        @Override
        void addSchema(DnCxt cxt, DnRawSchemaStore schemaStore) {
            schemaStore.addPackage(schemaPackage)
            schemaStore.addFunction("testEndpoint", {r -> testEndpoint(r)})
        }

        @Override
        Collection<Class> getStartupInitializers(DnCxt cxt) {
            return [DnSchemaService.class]
        }

        @Override
        Collection<Class> getServiceInitializers(DnCxt cxt) {
            return []
        }

        static void testEndpoint(DnRequestCxt requestCxt) {}
    }

    def "Test loading schema"() {
        LogStartup.log.debug(null, "Started test loading schema")
        when: "Creating a simple schema package."
        def fld1 = DnRawField.mkField("field1a", "Field 1 A", "The first field of first type")
        def fld2 = DnRawField.mkField("field1b", null, null).setTypeRef(DN_INTEGER)
        def type1a = DnRawType.mkType("FirstTypeA", [fld1, fld2])
        def fld3 = DnRawField.mkField("fieldComposed1", null, null).setTypeRef(type1a.name)
        def type1b = DnRawType.mkType("FirstTypeB", [fld1, fld2, fld3])

        def pckgName1 = "TestPackage1"
        String namespace = "test"
        def pkg1 = DnRawSchemaPackage.mkPackage("pckgName1", namespace, [type1a, type1b])
        def testComponent1 = new TestComponent(pkg1)
        def config = InstanceRegistry.getOrCreateInstanceConfig(pckgName1, [:], [testComponent1])
        def cxt = InstanceRegistry.createCxt("TestSchema1", config)
        def schemaStore = cxt.getSchema()
        def dnType1a = schemaStore?.getType("${namespace}.${type1a.name}")
        def dnType1b = schemaStore?.getType("${namespace}.${type1b.name}")

        then: "Schema store should have expected value"
        dnType1a != null
        dnType1a.fields?.collect {[it.name, it.typeRef]} == [["field1a", DN_STRING], ["field1b", DN_INTEGER]]
        dnType1b != null
        dnType1b.fieldsByName.get(fld3.name).typeRef == "${namespace}.${type1a.name}"
        LogStartup.log.debug(cxt, "Finished test loading schema")
    }

    def "Test creating endpoint"() {
        when: "Creating a simple endpoint"
        def fld1 = DnRawField.mkField("field1a", "Field 1 A", "The first field of first type")
        def fld2 = DnRawField.mkField("field1b", null, null).setTypeRef(DN_INTEGER)
        def inType1 = DnRawType.mkType("FirstInType", [fld1, fld2])
        def fld3 = DnRawField.mkField("field1b", "Field 1 B", "The response field")
        def outType1 = DnRawType.mkType("FirstOutType", [fld3])

        def endpointType1 = DnRawEndpoint.mkListEndpoint("/test/example", "testEndpoint", "Test of endpoint definition",
            "FirstInType", "FirstOutType")
        def pckgName1 = "TestEndpointPackage1"
        String namespace = "test"
        def pkg1 = DnRawSchemaPackage.mkPackage("pckgName1", namespace,
                [inType1, outType1, endpointType1.getRawType()])
        def testComponent1 = new TestComponent(pkg1)
        def config = InstanceRegistry.getOrCreateInstanceConfig(pckgName1, [:], [testComponent1])
        def cxt = InstanceRegistry.createCxt("TestEndpointSchema1", config)
        def schemaStore = cxt.getSchema()
        def endpointDef = schemaStore?.endpoints.get("/test/example")

        then: "Endpoint definition should be as expected"
        endpointDef != null
        endpointDef.inType.fields.size() == 3
        endpointDef.outType.fields.size() == 5
        endpointDef.inType.fieldsByName.limit != null
        endpointDef.outType.fieldsByName.items?.isList

    }
}
