package org.dynamicruntime.schemadef

import org.dynamicruntime.exception.DnException
import org.dynamicruntime.simulation.TestComponent
import org.dynamicruntime.startup.InstanceRegistry
import org.dynamicruntime.startup.LogStartup
import org.dynamicruntime.util.DnDateUtil
import spock.lang.Specification

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*


class DnSchemaServiceTest extends Specification {
    public static String namespace = "test"

    def "Test loading schema"() {
        LogStartup.log.debug(null, "Started test loading schema")
        when: "Creating a simple schema package."
        def fld1 = DnRawField.mkField("field1a", "Field 1 A", "The first field of first type")
        def fld2 = DnRawField.mkField("field1b", null, null).setTypeRef(DNT_INTEGER)
        def type1a = DnRawType.mkType("FirstTypeA", [fld1, fld2])
        def fld3 = DnRawField.mkField("fieldComposed1", null, null).setTypeRef(type1a.name)
        def type1b = DnRawType.mkType("FirstTypeB", [fld1, fld2, fld3])

        def cxt = createCxt("TestPackage1", [type1a, type1b])
        def schemaStore = cxt.getSchema()
        def dnType1a = schemaStore?.getType("${namespace}.${type1a.name}")
        def dnType1b = schemaStore?.getType("${namespace}.${type1b.name}")

        then: "Schema store should have expected value"
        dnType1a != null
        dnType1a.fields?.collect {[it.name, it.typeRef]} == [["field1a", DNT_STRING], ["field1b", DNT_INTEGER]]
        dnType1b != null
        dnType1b.fieldsByName.get(fld3.name).typeRef == "${namespace}.${type1a.name}"
        LogStartup.log.debug(cxt, "Finished test loading schema")

        when: "Testing schema validation"
        def input = [field1a: "x1", field1b: "25", fieldComposed1: [field1a: "x2", field1b: 25.2]]

        then: "Schema should be able to do simple conversion and validation"
        def validator = new DnSchemaValidator(cxt, DnSchemaValidator.REQUEST_MODE)
        def validated1 = validator.validateAndCoerce(dnType1b, input)
        // Integers should have been coerced
        validated1 == [field1a: "x1", field1b: 25, fieldComposed1: [field1a: "x2", field1b: 25]]
    }

    def "Test creating endpoint"() {
        when: "Creating a simple endpoint"
        def fld1 = DnRawField.mkField("field1a", "Field 1 A", "The first field of first type")
        def fld2 = DnRawField.mkField("field1b", null, null).setTypeRef(DNT_INTEGER)
        def inType1 = DnRawType.mkType("FirstInType", [fld1, fld2])
        def fld3 = DnRawField.mkField("field1b", "Field 1 B", "The response field")
        def outType1 = DnRawType.mkType("FirstOutType", [fld3])

        def endpointType1 = DnRawEndpoint.mkListEndpoint("/test/example", "testEndpoint", "Test of endpoint definition",
            "FirstInType", "FirstOutType")

        def cxt = createCxt("TestEndpointPackage1", [inType1, outType1, endpointType1])
        // Retrieve the loaded types.
        def schemaStore = cxt.getSchema()
        def endpointDef1 = schemaStore?.endpoints?.get("/test/example")

        then: "Endpoint definition should be as expected"
        endpointDef1 != null
        endpointDef1.method == "GET"
        endpointDef1.inType.fields.size() == 3
        endpointDef1.outType.fields.size() == 4
        endpointDef1.inType.fieldsByName.limit != null
        endpointDef1.outType.fieldsByName.items?.isList
    }

    def "Test schema validation"() {
        // We show how we can define types purely with maps. Useful here because we do not care
        // about labels and descriptions. Note that automatic namespace qualifiers are applied
        // to references to dnType entries as well as to the names of the entries (when it
        // is put into the package).
        def type1Name = "Type1"
        def type1 = [name: type1Name, dnFields: [
                [name: "strField"],
                [name: "dateField", required: true, dnTypeRef: "Date"],
                [name: "boolField", defaultValue: true, dnTypeRef: "Boolean"],
                [name: "listField", isList: true, dnTypeDef: [max: 10, baseType: "Integer"]],
                [name: "listMapField", isList: true, dnTypeDef: [
                        dnFields: [[name: "entry"]]
                ]],
                [name: "recursionField", dnTypeRef: type1Name]]]

        // We show how we can augment an existing type by modifying existing fields in Type1.
        def type2Name = "Type2"
        // Type2 is type1 except *listMapField is required.
        def type2 = [name: type2Name, baseType: type1Name, dnFields:
                [[name: "listMapField", required: true],
                [name: "recursionField", dnTypeRef: type2Name]]]
        def cxt = createCxt("ValidateTypes", [type1, type2].collect {DnRawType.extract(it)})
        // Get the manufactured read only type. Note that we now have to use the namespace qualifier to
        // the dnType entry.
        def dnType1 = cxt.getSchema().getType(namespace + "." + type1Name)
        def validator = new DnSchemaValidator(cxt, DnSchemaValidator.REQUEST_MODE)

        when: "Verifying a good case"
        def dateStr = "2018-06-06"
        def dateVal = DnDateUtil.parseDate(dateStr)
        // Note usage of both *dateStr* and *dateVal* in construct.
        def goodInput = [strField: "x1", dateField: dateStr, listField: "5,8",
                listMapField: [[entry: "x2"], [entry: "x3"]],
                recursionField: [dateField: dateVal, boolField: false, listField: [1,2]]]

        def goodResults = validator.validateAndCoerce(dnType1, goodInput)

        then: "Should get expected results"
        goodResults == [strField: "x1", dateField: dateVal, boolField: true, listField: [5,8],
                listMapField: [[entry: "x2"], [entry: "x3"]],
            recursionField: [dateField: dateVal, boolField: false, listMapField: [], listField: [1,2]]]

        when: "Verifying a required value"
        def emptyInput = [:]
        def emptyErrMsg = validateAndCaptureException(validator, dnType1, emptyInput)

        then: "Error message should have expected content"
        emptyErrMsg.contains("dateField") && emptyErrMsg.contains("Required")

        when: "Verifying error on field that should not be present"
        def unReferencedInput = [dateField: dateVal, recursionField: [dateField: dateVal, abc: 2]]
        def unreferencedErrMsg = validateAndCaptureException(validator, dnType1, unReferencedInput)

        then: "Should get an error saying extra field was supplied"
        unreferencedErrMsg.contains("recursionField") && unreferencedErrMsg.contains("abc") &&
            unreferencedErrMsg.contains("Extra")

        when: "Verifying a value that exceeds the maximum"
        def aboveMaxInput = [dateField: dateVal, listField: [1, 11]]
        def aboveMaxErrMsg =  validateAndCaptureException(validator, dnType1, aboveMaxInput)

        then: "Should get error message about exceeding the maximum"
        aboveMaxErrMsg.contains("listField") && aboveMaxErrMsg.contains("11") &&
            aboveMaxErrMsg.contains("10") && aboveMaxErrMsg.contains("not below")

        when: "Verifying previous good data with a modified type"
        def dnType2 = cxt.getSchema().getType(namespace + "." + type2Name)
        def missingListMsg = validateAndCaptureException(validator, dnType2, goodInput)

        then: "The good data should no longer pass because it is missing a list inside the recursionField."
        missingListMsg.contains("recursionField") && missingListMsg.contains("listMapField") &&
                missingListMsg.contains("Required")

        when: "Using new good data for type2"
        def goodInputForType2a = [dateField: dateStr, listMapField: [[entry: "x1"]]]
        def goodInputForType2b = [dateField: dateStr, listMapField: [[entry: "x1"]],
            recursionField: [dateField: dateStr, listMapField: [[entry: "x2"]]]]

        def goodResultsForType2a = validator.validateAndCoerce(dnType2, goodInputForType2a)
        def goodResultsForType2b = validator.validateAndCoerce(dnType2, goodInputForType2b)

        then: "Should get predicted results"
        goodResultsForType2a == [dateField: dateVal, boolField: true, listField: [], listMapField: [[entry: "x1"]]]
        goodResultsForType2b == [dateField: dateVal, boolField: true,  listField: [], listMapField: [[entry: "x1"]],
            recursionField: [dateField: dateVal, boolField: true, listField: [], listMapField: [[entry: "x2"]]]]
    }

    /** Instantiates a mini-app and gets a DnCxt for it. Note the usage of the TestComponent from the simulation
     * code. */
    def createCxt(String pckgName, List<DnRawTypeInterface> types) {
        // Package up our types.
        def pkg1 = DnRawSchemaPackage.mkPackage(pckgName, namespace, types)
        // Create a component to load.
        def testComponent = new TestComponent(pkg1)
        // Load component, create an instance, and get its config data.
        def config = InstanceRegistry.getOrCreateInstanceConfig(pckgName, [:], [testComponent])
        // Create an context for our instance
        return InstanceRegistry.createCxt(pckgName, config)
    }

    String validateAndCaptureException(DnSchemaValidator validator, DnType type, Map<String,Object> data) {
        String msg = "no error"
        try {
            validator.validateAndCoerce(type, data)
        } catch (DnException e) {
            msg = e.getFullMessage()
        }
        return msg
    }
}
