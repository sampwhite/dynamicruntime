package org.dynamicruntime.node

import org.dynamicruntime.CoreComponent
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.startup.InstanceRegistry
import spock.lang.Specification

/** This test is more complicated than it appears. It is based on performing a full initialization of the stack,
 * and as such it takes longer to run than other tests. */
class DnCoreNodeServiceTest extends Specification {
    def setupSpec() {
        InstanceRegistry.addComponentDefinitions([new CoreComponent(), new CommonComponent()])
    }

    def "Test encryption using secret key put into database."() {
        def cxt = createCxt("testEncryption")
        def coreNodeService = DnCoreNodeService.get(cxt)
        String text = "abc"
        when: "Encoding plain text"
        def encodedText = coreNodeService.encryptString(text)

        then: "Decoding should produce original text"
        coreNodeService.decryptString(encodedText) == text
    }

    /** Full common & core initialization */
    static def createCxt(String cxtName) {
        def config = InstanceRegistry.getOrCreateInstanceConfig("coreNode", [:])
        return InstanceRegistry.createCxt(cxtName, config)
    }
}
