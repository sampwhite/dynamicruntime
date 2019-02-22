package org.dynamicruntime.common.user

import org.dynamicruntime.common.mail.DnMailService
import org.dynamicruntime.common.startup.StartupCommon
import org.dynamicruntime.context.DnCxt
import org.dynamicruntime.servlet.DnTestServletClient
import org.dynamicruntime.util.PageUtil
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * Though this is run as a unit test, its flavor is closer to that of an integration or
 * functional test. It walks through a lot of the registration and login logic.
 *
 * Each test builds on data put in by prior test. A win for the Spock test framework that lets us do this. */
@Stepwise
class AuthFormHandlerTest extends Specification {
    static def jason = "jason"
    static def jasonEmail = "jason@example.com"
    static def jasonPassword = "xyz89@"
    static def jasonPassword2 = "xyz89!"
    static def machineIP1 = "10.10.10.10"
    static def machineIP2 = "10.10.10.11"

    def "Test registering, logout, and login endpoints"() {
        def cxt = createCxt("registerAndLogin")
        def servletClient = new DnTestServletClient(cxt.instanceConfig)
        // Used by login sources tracking.
        servletClient.setHeader("User-Agent", "Fake Chrome (Internal OS)")
        servletClient.setHeader("X-Forwarded-For", machineIP1)

        // Get the form auth tokens we need for later requests.
        // Cast to Map<String,Object> to fix IntelliJ issue where it gives analyze warnings when adding maps
        // with a plus symbol that are not of exactly the same type.
        def ftData = getFormAuthToken(servletClient) as Map<String,Object>

        when: "Creating the initial user row"
        // Create the jason user.
        // First we register the email (which creates the initial user row).
        def contactData = [contactAddress: jasonEmail, contactType: "email"] as Map<String,Object>
        def reqVerifyData = contactData + ftData
        def verifyCode = getVerifyCode(cxt, servletClient, "/auth/newContact/sendVerify",
                jasonEmail, reqVerifyData)

        def verifyData = [formAuthToken: ftData.formAuthToken, verifyCode: verifyCode] as Map<String,Object>
        def reqCreateInitialData = contactData + verifyData
        def reqCreateInitialResponse = servletClient.sendJsonPutRequest("/auth/user/createInitial",
            reqCreateInitialData)
        // We should now have a userId.
        def userId = reqCreateInitialResponse.userId as Long

        then: "Should get an established userId for a row that was created"
        userId != null

        when: "Filling out the user with a username and password"
        def userData = [userId: userId, username: jason, password: jasonPassword] as Map<String,Object>
        def reqUserData = userData + verifyData
        def setLoginDataResponse = servletClient.sendJsonPutRequest("/auth/user/setLoginData", reqUserData)
        def profileData = servletClient.sendJsonGetRequest("/user/self/info", [:])

        then: "We should be logged in as that user."
        setLoginDataResponse.userId == userId
        profileData.userId == userId
        profileData.publicName == jason

        when: "Doing a logout followed by a simple login"
        // Logout does not actually send back response data, it does a redirect instead (which we ignore for now).
        servletClient.sendGetRequest("/logout", [:])
        def profileDataAfterLogout = servletClient.sendJsonGetRequest("/user/self/info", [:])

        // Do a standard login.
        def ftData2 = getFormAuthToken(servletClient) as Map<String,Object>
        def usernameAndPassword = [username: jason, password: jasonPassword] as Map<String,Object>
        def loginParams = usernameAndPassword + ftData2
        def loginResponse = servletClient.sendJsonPostRequest("/auth/login/byPassword", loginParams)
        def profileDataAfterLogin = servletClient.sendJsonGetRequest("/user/self/info", [:])

        then: "The logout and login should have happened"
        profileDataAfterLogout.httpCode == 401
        profileDataAfterLogout.userId == null
        loginResponse.httpCode == null // httpCode only set on error responses.
        profileDataAfterLogin.userId == userId
    }

    def "Test logic used to detect a *familiar* browser and use alternative login paths"() {
        def cxt = createCxt("testLoginSources")
        // Note that we get none of the cookies from prior test. We are essentially starting over.
        def servletClient = new DnTestServletClient(cxt.instanceConfig)
        // Used by login sources tracking.
        servletClient.setHeader("User-Agent", "Fake Chrome (Internal OS)")
        // Use a different IP address from before.
        servletClient.setHeader("X-Forwarded-For", machineIP2)

        // Attempt normal login with the user we created in the prior test (using @Stepwise to guarantee order).
        when: "Attempting a login from a browser that is not from the same IP address and has no sourceId cookie"
        def ftData1 = getFormAuthToken(servletClient) // Do not need to cast because everything is Map<String,String>.
        def usernameAndPassword = [username: jason, password: jasonPassword]
        def loginParams = usernameAndPassword + ftData1
        def loginResponse1a = servletClient.sendJsonPostRequest("/auth/login/byPassword", loginParams)

        then: "Simple login should fail with a 403"
        loginResponse1a.httpCode == 403

        when: "Doing a login using a verification code"
        def sendCodeData = [username: jason] + ftData1
        String code = getVerifyCode(cxt, servletClient, "/auth/user/sendVerify", jasonEmail, sendCodeData)

        def loginByCodeData = [username: jason, formAuthToken: ftData1.formAuthToken, verifyCode: code]
        def loginResponse1b = servletClient.sendJsonPostRequest("/auth/login/byCode", loginByCodeData)
        def profileData = servletClient.sendJsonGetRequest("/user/self/info", [:])

        then: "Should successfully login"
        loginResponse1b.httpCode == null
        profileData.publicName == jason

        when: "Logging in using the IP address from prior test but different browser"
        servletClient.sendGetRequest("/logout", [:]) // Logout first, though not actually necessary.
        def servletClient2 = new DnTestServletClient(cxt.instanceConfig)
        servletClient2.setHeader("User-Agent", "Fake Firefox (Internal OS)")
        servletClient2.setHeader("X-Forwarded-For", machineIP1)
        def ftData2 = getFormAuthToken(servletClient2)
        def loginParams2 = usernameAndPassword + ftData2
        def loginResponse2 = servletClient2.sendJsonPostRequest("/auth/login/byPassword", loginParams2)

        then: "Should successfully so a simple login and have captured IP addresses and user agents"
        loginResponse2.httpCode == null
        loginResponse2.publicName == jason
        def capturedIps = loginResponse2.userProfileData?.loginSources?.capturedIps
        capturedIps != null
        capturedIps.size() == 2
        def cIpsMap = capturedIps.collectEntries {[it.ipAddress, it]}
        def userAgents1 = cIpsMap[machineIP1]?.userAgents?.sort() as List<String>
        userAgents1 != null
        userAgents1?.size() == 2
        userAgents1[0].contains("Chrome")
        userAgents1[1].contains("Firefox")
        def userAgents2 = cIpsMap[machineIP2]?.userAgents as List<String>
        userAgents2?.size() == 1
        userAgents2[0].contains("Chrome")
    }

    def "Test forgot password logic"() {
        def cxt = createCxt("testLoginSources")
        // Note that we get none of the cookies from prior test. We are essentially starting over.
        def servletClient = new DnTestServletClient(cxt.instanceConfig)
        // Use user agent and ip address from first test (though this should not matter).
        servletClient.setHeader("User-Agent", "Fake Safari (Internal OS)")
        servletClient.setHeader("X-Forwarded-For", machineIP1)

        when: "Using verification code to allow us to login and change password"
        def ftData = getFormAuthToken(servletClient)
        def sendCodeData = [username: jason] + ftData
        String code = getVerifyCode(cxt, servletClient, "/auth/user/sendVerify", jasonEmail, sendCodeData)
        def loginByCodeData = [username: jason, password: jasonPassword2, formAuthToken: ftData.formAuthToken,
            verifyCode: code]
        def loginResponse1 = servletClient.sendJsonPostRequest("/auth/login/byCode", loginByCodeData)

        then: "Should have logged in"
        loginResponse1.publicName == jason

        when: "Logging in with prior password"
        // Logout first, though not actually necessary because you can attempt to login (and even become a
        // different user) even if you are already logged in.
        servletClient.sendGetRequest("/logout", [:])
        // Attempt login, we can continue to use are old ftData (or regenerate if we wish).
        // ftData = getFormAuthToken(servletClient) // Uncomment, should make no difference.
        def oldLoginParams = [username: jason, password: jasonPassword] + ftData
        def loginResponse2 = servletClient.sendJsonPostRequest("/auth/login/byPassword", oldLoginParams)

        then: "Should fail with invalid password"
        loginResponse2.httpCode == 400
        (loginResponse2.message as String)?.toLowerCase()?.contains("password")

        when: "Logging in with new password"
        def newLoginParams = [username: jason, password: jasonPassword2] + ftData
        def loginResponse3 = servletClient.sendJsonPostRequest("/auth/login/byPassword", newLoginParams)

        then: "Should have logged in"
        loginResponse3.httpCode == null
        loginResponse3.publicName == jason
    }

    /** Full common & core initialization */
    static def createCxt(String cxtName) {
        // Other tests that use the same instance name will not pay a startup cost, but they
        // will share instance data (which is either a good thing or a bad thing based on the test).
        return StartupCommon.mkBootCxt(cxtName, "sharedTestInstance", [:])
    }

    static Map<String,String> getFormAuthToken(DnTestServletClient servletClient) {
        def resp =  servletClient.sendJsonGetRequest("/auth/form/createToken", [:])
        String ft = resp.formAuthToken
        Map cp = resp.captchaData as Map
        String fc = cp.formAuthCode
        return [formAuthToken: ft,  formAuthCode: fc]
    }

    static String getLastEmailMessage(DnCxt cxt, String email) {
        def mailService = DnMailService.get(cxt)
        def recentEmails = mailService.getRecentSentEmails().reverse()
        def ours = recentEmails.find {re -> re.mailData.to == email}
        return ours?.mailData?.text
    }

    static String getDnCodeFromLastEmail(DnCxt cxt, String email) {
        String lastMessage = getLastEmailMessage(cxt, email)
        if (!lastMessage) {
            return ""
        }
        // Use the fact that the code starts with a DN- and ends with a period in the text of the email.
        return PageUtil.extractSection(lastMessage, "DN-", ".")
    }

    static String getVerifyCode(DnCxt cxt, DnTestServletClient servletClient, String endpoint,
                                String email, Map<String,Object> data) {
        // We do not need to worry about response of actual call.
        servletClient.sendJsonPostRequest(endpoint, data)
        // We actually only care about the email that was generated that gets captured in memory
        // just so we can access it during a test.
        return getDnCodeFromLastEmail(cxt, email)
    }

}
