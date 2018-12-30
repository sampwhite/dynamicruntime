package org.dynamicruntime.common.user

import org.dynamicruntime.CoreComponent
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.simulation.TestComponent
import org.dynamicruntime.sql.SqlCxt
import org.dynamicruntime.sql.topic.SqlTopicConstants
import org.dynamicruntime.sql.topic.SqlTopicService
import org.dynamicruntime.sql.topic.SqlTopicTranProvider
import org.dynamicruntime.startup.ComponentDefinition
import org.dynamicruntime.startup.InstanceRegistry
import org.dynamicruntime.util.ConvertUtil
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

    SqlCxt createSqlCxt(String cxtName, String shard = "primary") {
        // Create a component to load.
        List<ComponentDefinition> compList = []
        compList.addAll([new TestComponent([UserSchemaDefData.getPackage()], [UserService.class])])
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
