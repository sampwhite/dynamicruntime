package org.dynamicruntime.common.mail

import org.dynamicruntime.common.startup.StartupCommon
import spock.lang.Specification

class DnMailServiceTest extends Specification {
    def "Simulated email should get captured"() {
        def cxt = createCxt("simpleMailTest")
        when: "Sending a simulated email"
        def mailService = DnMailService.get(cxt)
        def mailData = mailService.createMailData("xxx&example.com", "tester@testit.com", "Testing email",
            "We are testing email", null)
        def resp = mailService.sendEmail(cxt, mailData)

        then: "Should successfully do simulation send"
        resp != null

        // Note, we are using a shared instance, so we cannot assume that our email is the only one in the
        // captured email.
        when: "Examining current email"
        def ourEmail = mailService.getRecentSentEmails().find {it.id == resp.id}

        then: "We should find our email"
        ourEmail != null
    }

    /** Full common & core initialization */
    static def createCxt(String cxtName) {
        // Other tests that use the same instance name will not pay a startup cost, but they
        // will share instance data (which is either a good thing or a bad thing based on the test).
        return StartupCommon.mkBootCxt(cxtName, "sharedTestInstance", [:])
    }

}
