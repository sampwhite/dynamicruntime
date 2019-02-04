package org.dynamicruntime.node

import org.dynamicruntime.CoreComponent
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.common.startup.StartupCommon
import org.dynamicruntime.startup.InstanceRegistry
import spock.lang.Specification

/** This test is more complicated than it appears. It is based on performing a full initialization of the stack,
 * and as such it takes longer to run than other tests. */
class DnCoreNodeServiceTest extends Specification {
    def "Test encryption using secret key put into database."() {
        def cxt = createCxt("testEncryption")
        def coreNodeService = DnCoreNodeService.get(cxt)
        String text = "abc"
        when: "Encoding plain text"
        // At this point a database table has been loaded with an internal encryption key.
        def encodedText = coreNodeService.encryptString(text)

        then: "Decoding should produce original text"
        coreNodeService.decryptString(encodedText) == text
    }

    /** Full common & core initialization */
    static def createCxt(String cxtName) {
        // Other tests that use the same instance name will not pay a startup cost, but they
        // will share instance data (which is either a good thing or a bad thing based on the test).
        return StartupCommon.mkBootCxt(cxtName, "sharedTestInstance", [:])
    }
}
