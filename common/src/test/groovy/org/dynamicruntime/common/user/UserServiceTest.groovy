package org.dynamicruntime.common.user

import org.dynamicruntime.CoreComponent
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.endpoint.NodeEndpoints
import org.dynamicruntime.node.DnCoreNodeService
import org.dynamicruntime.schemadata.NodeCoreSchema
import org.dynamicruntime.servlet.DnRequestService
import org.dynamicruntime.servlet.DnTestServletClient
import org.dynamicruntime.simulation.TestComponent
import org.dynamicruntime.sql.SqlCxt
import org.dynamicruntime.sql.topic.SqlTopicConstants
import org.dynamicruntime.sql.topic.SqlTopicService
import org.dynamicruntime.sql.topic.SqlTopicTranProvider
import org.dynamicruntime.startup.ComponentDefinition
import org.dynamicruntime.startup.InstanceRegistry
import org.dynamicruntime.user.UserConstants
import org.dynamicruntime.util.ConvertUtil
import org.dynamicruntime.util.ParsingUtil
import spock.lang.Specification

class UserServiceTest extends Specification {
    // Enable to run against postgresql (assuming you have created private/dnConfig.yaml
    // with appropriate entries in some parent directory).
    public static boolean executeAsIntegrationTest = false

    def "Test user service startup"() {
        when: "Creating UserService initialization and retrieving sysadmin record"
        def sqlCxt = createSqlCxt("validateStartup")
        def cxt = sqlCxt.cxt
        def curDate = ConvertUtil.fmtObject(cxt.now())
        def sqlDb = sqlCxt.sqlDb
        AuthQueryHolder qh = AuthQueryHolder.get(sqlCxt)
        def sysadminData = [:]
        sqlDb.withSession(cxt) {
            sysadminData = sqlDb.queryOneEnabled(cxt, qh.qUsername, [username : "sysadmin"])
        }
        def sysAuthUser = AuthUser.extract(sysadminData)

        then: "Should get initial sysadmin user"

        sysAuthUser.userId > 0
        sysAuthUser.roles.contains("admin")

        when: "Updating sysadmin record"
        sysAuthUser.roles.add("testRole")
        sysAuthUser.authUserData.lastUpdate = curDate

        SqlTopicTranProvider.executeTopicTran(sqlCxt, "testSysAdminTran", "update1",
                [userId: sysAuthUser.userId]) {
            sqlCxt.tranData.putAll(sysAuthUser.toMap())
        }

        def sysAuthUser2 = null
        sqlDb.withSession(cxt) {
            sysAuthUser2 = AuthUser.extract(sqlDb.queryOneEnabled(cxt, sqlCxt.sqlTopic.qTranLockQuery,
                    [userId : sysAuthUser.userId]))
        }

        then: "Should get updated user"
        sysAuthUser.roles.contains("testRole")
        sysAuthUser.authUserData.lastUpdate == curDate
    }

    def "Test auth token creation"() {
        def sqlCxt = createSqlCxt("validateAuthTokens")
        def cxt = sqlCxt.cxt
        // Go back 61 days.
        cxt.nowTimeOffsetInSeconds = -61*24*1000;
        def userService = UserService.get(cxt)

        when: "Adding an auth token for sysadmin 61 days ago"
        userService.addToken(cxt, "sysadmin", "testTokenId", "abc", [:], null)

        then: "Should get nothing if provide wrong token value"
        def ua1 = userService.queryToken(cxt, "testTokenId", "xyz")
        ua1 == null

        then: "Should get something if provide correct token value"
        def ua2 = userService.queryToken(cxt, "testTokenId", "abc")
        ua2?.username == "sysadmin"

        when: "Advancing to current time"
        cxt.nowTimeOffsetInSeconds = 0

        then: "Should not get something even if provide correct token value"
        def ua3 = userService.queryToken(cxt, "testTokenId", "abc")
        ua3 == null
    }

    def "Validate request execution can use token to become sysadmin"() {
        String token = "xyz"
        def sqlCxt = createSqlCxt("validateAuthTokens")
        def cxt = sqlCxt.cxt
        def userService = UserService.get(cxt)
        when: "Adding a valid auth token for sysadmin and executing a request using the token"
        userService.addToken(cxt, "sysadmin", "validateTokenId", token, [:], null)
        def servletClient = new DnTestServletClient(cxt.instanceConfig)
        servletClient.setHeader(UserConstants.AUTH_HDR_TOKEN, "validateTokenId#${token}")

        then: "Executing a request should populate an appropriate response with an authenticated user"
        def reqHandler = servletClient.sendGetRequest("/health/info", [:])
        reqHandler.rptStatusCode == 200
        def createdCxt = reqHandler?.createdCxt
        createdCxt != null
        def userProfile = createdCxt.userProfile
        userProfile?.userId == 1
        userProfile.authId == "validateTokenId"
        ParsingUtil.toJsonMap(reqHandler.rptResponseData)?.requestUri?.contains("/health/info")
    }

    SqlCxt createSqlCxt(String cxtName, String shard = "primary") {
        // Create a component to load.
        List<ComponentDefinition> compList = []
        def tc = new TestComponent([UserSchemaDefData.getPackage(), NodeCoreSchema.getPackage()],
                [UserService.class, DnRequestService.class, DnCoreNodeService.class])
        tc.endpointFunctions.addAll(NodeEndpoints.getFunctions())
        compList.addAll([tc])
        if (executeAsIntegrationTest) {
            // Test will use postgresql instance on local machine that is assumed to have appropriate
            // database *data1* created for *java_user*.
            InstanceRegistry.setEnvName("integration")
            List<ComponentDefinition> stdComps = [new CoreComponent(), new CommonComponent()]
            compList.addAll(stdComps)
        }
        def config = InstanceRegistry.getOrCreateInstanceConfig("testUserService", [:], compList)
        // Create an context for our instance
        def cxt = InstanceRegistry.createCxt(cxtName, config)
        cxt.shard = shard

        return SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC)
    }

}
