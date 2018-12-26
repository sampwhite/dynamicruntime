package org.dynamicruntime.sql

import org.dynamicruntime.CoreComponent
import org.dynamicruntime.common.CommonComponent
import org.dynamicruntime.schemadef.DnField
import org.dynamicruntime.schemadef.DnRawField
import org.dynamicruntime.schemadef.DnRawSchemaPackage
import org.dynamicruntime.schemadef.DnRawTable
import org.dynamicruntime.schemadef.DnRawTypeInterface
import org.dynamicruntime.schemadef.DnTable
import org.dynamicruntime.simulation.TestComponent
import org.dynamicruntime.startup.ComponentDefinition
import org.dynamicruntime.startup.InstanceRegistry
import org.dynamicruntime.util.ConvertUtil
import org.dynamicruntime.util.ParsingUtil
import spock.lang.Specification

import java.sql.ResultSet
import java.sql.ResultSetMetaData

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*


class SqlDatabaseTest extends Specification {
    // Enable to run against postgresql (assuming you have created private/dnConfig.yaml
    // with appropriate entries in some parent directory).
    public static boolean executeAsIntegrationTest = false

    public static String namespace = "test"

    def "Test creating a database table and adding a row"() {

        def predictedNamesAndTypes = ['userId:Integer', 'userGroup:String', 'total:Float',
                                      'enabled:Boolean', 'createdDate:Date', 'modifiedDate:Date']
        when: "Defining a simple table"

        def total = DnRawField.mkField("total", "Total", "A user's total").setTypeRef(DN_FLOAT)
        def userTotalTable = DnRawTable.mkStdUserTable("UserTotal", "User totals",
                [total], ["userId"])
        // Allows ranged queries on enabled totals.
        userTotalTable.setSimpleIndexes([['enabled', 'total']])

        def cxt = createCxt("TestUserTotal", [userTotalTable])
        def schemaStore = cxt.getSchema()

        then: "Should have produced a full schema definition for table"
        def fullTbType = schemaStore.getType("test.UserTotalTable")
        fullTbType != null
        fullTbType.fields.collect {"${it.name}:${it.coreType}".toString()} == predictedNamesAndTypes
        fullTbType.fieldsByName.userId?.isRequired
        !fullTbType.fieldsByName.total?.isRequired
        fullTbType.fieldsByName.createdDate.coreType == "Date"

        then: "Should a full table definition usable for creating tables"
        def tableDef = schemaStore.getTable("UserTotal")
        tableDef != null
        tableDef.primaryKey.fieldDeclarations == ['userId']
        tableDef.indexes.collect {it.fieldDeclarations} == [['modifiedDate'], ['userGroup', 'modifiedDate'], ['enabled', 'total']]

        when: "Creating a database table using the definition"
        def builder = new SqlDbBuilder(cxt, "primary", !executeAsIntegrationTest)
        def sqlDb = builder.createDatabase()
        def sqlCxt = new SqlCxt(cxt, sqlDb, "test")

        // Create the table.
        sqlDb.withSession(cxt, {SqlTableUtil.createTable(sqlCxt, tableDef)})

        // Get table fields and types converted back to schema fields and types.
        Map<String,String> fieldAndTypes = getFieldsAndTypes(sqlCxt, tableDef.tableName)

        then: "Should get a table"
        fieldAndTypes.collect {k, v -> "$k:$v".toString()} == predictedNamesAndTypes

        when: "Adding two columns to a table"
        def newField1 = DnField.mkSimple("testField1", "Boolean")
        def newField2 = DnField.mkSimple("testField2", "Float")
        List<DnField> newFields = [] + tableDef.columns + [newField1, newField2]
        def newTableDef = new DnTable(tableDef.tableName, newFields, tableDef.primaryKey, tableDef.indexes,
                tableDef.data)

        // Update the table (by doing call to create it).
        sqlDb.withSession(cxt, {SqlTableUtil.createTable(sqlCxt, newTableDef)})

        // Get table fields and types converted back to schema fields and types after having added column.
        Map<String,String> newFieldsAndTypes = getFieldsAndTypes(sqlCxt, tableDef.tableName)
        predictedNamesAndTypes.addAll(["testField1:Boolean", "testField2:Float"])

        then: "Should have additional column"
        newFieldsAndTypes.collect {k, v -> "$k:$v".toString()} == predictedNamesAndTypes

        when: "Checking indexes"
        def summaries = getIndexInfo(sqlCxt, tableDef.tableName).sort()
        // Note star on end means that the index also has a uniqueness constraint.
        def predictedIndexes = ["enabled:total", "modifiedDate", "userGroup:modifiedDate", "userId*"]
        then: "Should have the expected indexes"
        summaries == predictedIndexes

        when: "Converting queries that use fields to ones that use columns"
        def testStr = "abc 'cde '' c:userId'''(c:userId) t:xyz t: :p : 'x t:UserTotal'"
        def predictedTbName = sqlDb.mkSqlTableName(sqlCxt, "xyz")
        def predictedStr = "abc 'cde '' c:userId'''(user_id) ${predictedTbName} t: ? : 'x t:UserTotal';"
        def testStmt = SqlStmtUtil.prepareSql(sqlCxt, "testString", newTableDef.columns, testStr)

        then: "Should get predicted outcome"
        testStmt.sql == predictedStr
        testStmt.bindFields == ["p"] as String[]

        when: "Building queries"
        String insertQuery = SqlStmtUtil.mkInsertQuery(newTableDef.tableName, newTableDef.columns, null)
        DnSqlStatement insertStmt = SqlStmtUtil.prepareSql(sqlCxt, "i" + newTableDef.tableName,
                newTableDef.columns, insertQuery)
        String resultsQuery = "select * from t:${newTableDef.tableName}"
        DnSqlStatement resultsStmt = SqlStmtUtil.prepareSql(sqlCxt, "q" + newTableDef.tableName,
            newTableDef.columns, resultsQuery)

        then: "Should get expected result"
        insertStmt.bindFields.size() == 8
        resultsStmt.bindFields.size() == 0

        when: "Inserting a row using insert query and querying back for it"
        def now = new Date()
        def data = [userId: 1, userGroup: "main", total: 2.02, enabled: true,
                    createdDate: now, modifiedDate: now, testField2: 3.01]
        def results = []
        sqlDb.withSession(cxt, {
            sqlDb.executeDnStatement(cxt, insertStmt, data)
            results = sqlDb.queryDnStatement(cxt, resultsStmt, [:])
        })
        def resultsStr = ConvertUtil.fmtObject(results)
        println(ConvertUtil.fmtObject(results))
        def expectedStr = ConvertUtil.fmtObject([data])

        then: "Should have inserted data and gotten it back"
        resultsStr == expectedStr
    }

    Map<String,String> getFieldsAndTypes(SqlCxt sqlCxt, String tableName) {
        def cxt = sqlCxt.cxt
        def sqlDb = sqlCxt.sqlDb
        def realTableName = sqlDb.mkSqlTableName(sqlCxt, tableName)
        Map<String,String> fieldAndTypes = [:]
        def aliases = sqlDb.getAliases(sqlCxt.topic)
        sqlDb.withSession(cxt, {
            def conn = sqlDb.getMustExist(cxt).getConnection()
            def dbMetadata = conn.getMetaData()
            ResultSet rs = dbMetadata.getColumns(null, null, realTableName, null)
            while (rs.next()) {
                // H2 stores column names in upper case.
                String colName = rs.getString("COLUMN_NAME").toLowerCase()
                String fldName = aliases.getFieldName(colName)
                int jdbcType = rs.getInt("DATA_TYPE")
                String dnType = SqlTypeUtil.toDnType(jdbcType)
                fieldAndTypes.put(fldName, dnType)
            }
        })
        return fieldAndTypes
    }

    def getIndexInfo(SqlCxt sqlCxt, String tableName) {
        def cxt = sqlCxt.cxt
        def sqlDb = sqlCxt.sqlDb
        def realTableName = sqlDb.mkSqlTableName(sqlCxt, tableName)
        def aliases = sqlDb.getAliases(sqlCxt.topic)
        List<String> indexSummaries = []
        sqlDb.withSession(cxt, {
            def conn = sqlDb.getMustExist(cxt).getConnection()
            def dbMetaData = conn.getMetaData()
            ResultSet rs = dbMetaData.getIndexInfo(null, null, realTableName, false, false)
            boolean isUnique = false
            String curIndexName = null
            List<String> curEntryList = []
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME")
                String colName = rs.getString("COLUMN_NAME").toLowerCase()
                String fieldName = aliases.getFieldName(colName)
                boolean nonUnique = rs.getBoolean("NON_UNIQUE")
                if (curIndexName == null || curIndexName != indexName) {
                    if (curEntryList.size() > 0) {
                        String u = (isUnique) ? "*" : ""
                        indexSummaries.add(curEntryList.join(":") + u)
                        curEntryList.clear()
                    }
                    isUnique = !nonUnique
                    curIndexName = indexName
                }
                curEntryList.add(fieldName)
            }
            if (curEntryList.size() > 0) {
                String u = (isUnique) ? "*" : ""
                indexSummaries.add(curEntryList.join(":") + u)
            }
        })

        return indexSummaries
    }

    /** Instantiates a mini-app and gets a DnCxt for it. Note the usage of the TestComponent from the simulation
     * code. */
    def createCxt(String pckgName, List<DnRawTypeInterface> types) {
        // Package up our types.
        def pkg1 = DnRawSchemaPackage.mkPackage(pckgName, namespace, types)
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
        return InstanceRegistry.createCxt(pckgName, config)
    }

    /** Present for investigative purposes. */
    def dumpResultSet(ResultSet rs) {
        ResultSetMetaData md = rs.getMetaData()
        while (rs.next()) {
            Map m = [:]
            for (int i = 0; i < md.getColumnCount(); i++) {
                String fldName = md.getColumnName(i + 1)
                Object o = rs.getObject(fldName)
                if (o != null) {
                    m.put(fldName, o)
                }
            }
            println(ParsingUtil.toJsonString(m))
        }
        rs.first()
    }

}
