package org.dynamicruntime.sql.topic

import org.dynamicruntime.CoreComponent
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.context.UserProfile
import org.dynamicruntime.schemadef.DnRawField
import org.dynamicruntime.schemadef.DnRawSchemaPackage
import org.dynamicruntime.schemadef.DnRawTable
import org.dynamicruntime.schemadef.DnRawTypeInterface
import org.dynamicruntime.simulation.TestComponent
import org.dynamicruntime.sql.SqlCxt
import org.dynamicruntime.sql.SqlStmtUtil
import org.dynamicruntime.startup.ComponentDefinition
import org.dynamicruntime.startup.InstanceRegistry
import spock.lang.Specification

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*

class SqlTopicTest extends Specification {
    public static String namespace = "test"
    public static String tranTableName = "DnUsers"

    // Enable to run against postgresql (assuming you have created private/dnConfig.yaml
    // with appropriate entries in some parent directory).
    public static boolean executeAsIntegrationTest = false

    def "Test accessing and using an SQL Topic"() {
        def sqlCxt = createSqlCxt("topicTestPackage", [])
        def cxt = sqlCxt.cxt
        when: "Getting a test topic"
        def sqlTopic = sqlCxt.sqlTopic

        then: "Should get a topic"
        sqlTopic != null
        sqlTopic.table != null

        when: "Inserting two rows into the transaction table"
        def sqlDb = sqlTopic.sqlDb
        // Create a query to query all rows.
        def qRowsStmt = SqlStmtUtil.prepareSql(sqlCxt, "qAll${sqlTopic.table.tableName}",
                sqlTopic.table.columns, "select * from t:${sqlTopic.table.tableName}")
        List<Long> ids = []
        List<Map<String,Object>> rows = []
        def userData = [x: (long)Integer.MAX_VALUE + 1, y: 2]
        sqlDb.withSession(cxt) {
            long[] id = [-1] as long[]
            def row = [userGroup: "public", userData: userData, lastTranId: "insert", touchedDate: cxt.now()]
            SqlTopicUtil.prepForStdExecute(cxt, row) // Adds createdDate and modifiedDate.
            sqlDb.executeDnStatementGetCounterBack(cxt, sqlTopic.iTranLockQuery, row, id)
            ids.add(id[0])
            sqlDb.executeDnStatementGetCounterBack(cxt, sqlTopic.iTranLockQuery, row, id)
            ids.add(id[0])
            def curRows = sqlDb.queryDnStatement(cxt, qRowsStmt, [:]).sort {it.userId}
            rows.addAll(curRows)
        }

        then: "Should get inserted rows"
        rows.size() == 2
        rows.every {it.userData == userData}
        ids == [1L, 2L]
        rows.collect {it.userId} == ids

        when: "Performing transactions"
        // Do explicit provision of userId.
        SqlTopicTranProvider.executeTopicTran(sqlCxt, "testUpdateTran", "update1", [userId:1]) {
            // Current tran row should be magically provided as *tranData* in the *sqlCxt* object.
            sqlCxt.tranData.userData.x += 1
        }

        // The user profile should dictate the row being edited.
        cxt.userProfile = new UserProfile(2, "local", "local", [:])
        SqlTopicTranProvider.executeTopicTran(sqlCxt, "testUpdateTran", "update2", [:]) {
            sqlCxt.tranData.userData.x += 1
        }
        def rows2 = []
        sqlDb.withSession(cxt) {
            def curRows = sqlDb.queryDnStatement(cxt, qRowsStmt, [:]).sort {it.userId}
            rows2.addAll(curRows)
        }
        def userData2 = [x: (long)Integer.MAX_VALUE + 2, y: 2]

        then: "Should update the row"
        rows2.size() == 2
        rows2.every {it["userData"] == userData2}
    }

    SqlCxt createSqlCxt(String pckgName, List<DnRawTypeInterface> types, String shard = "primary") {
        // Provision *test* topic's transaction table.
        def userData = DnRawField.mkField("userData", "User Data",
                "Various different types of user data").setTypeRef(DNT_MAP)
        DnRawTypeInterface usersTable = DnRawTable.mkStdUserTopLevelTable(tranTableName, "Users table",
                [userData], null).setCounterField("userId")
        def allTypes = types + usersTable

        // Package up our types.
        def pkg1 = DnRawSchemaPackage.mkPackage(pckgName, namespace, allTypes)
        // Create a component to load.
        def compList = [new TestComponent(pkg1)] as List<ComponentDefinition>
        if (executeAsIntegrationTest) {
            // Test will use postgresql instance on local machine that is assumed to have appropriate
            // database *data1* created for *java_user*.
            InstanceRegistry.setEnvName("integration")
            List<ComponentDefinition> stdComps = [new CoreComponent(), new CommonComponent()]
            compList.addAll(stdComps)
        }
        def config = InstanceRegistry.getOrCreateInstanceConfig(pckgName, [:], compList)
        // Create an context for our instance
        def cxt = InstanceRegistry.createCxt(pckgName, config)
        cxt.shard = shard

        def testTopicInfo = SqlTopicInfo.mkShardsInSeparateStorage(tranTableName)
        def topicService = SqlTopicService.get(cxt)
        topicService.registerTopicContainer("test", testTopicInfo)
        SqlTopic topic = topicService.getOrCreateTopic(cxt, "test")
        return new SqlCxt(cxt, topic)
    }

}
