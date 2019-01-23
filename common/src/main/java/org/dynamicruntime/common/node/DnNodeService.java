package org.dynamicruntime.common.node;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.node.DnAuthConfig;
import org.dynamicruntime.node.DnCoreNodeService;
import org.dynamicruntime.node.DnNodeId;
import org.dynamicruntime.schemadef.DnSchemaDefConstants;
import org.dynamicruntime.schemadef.DnTable;
import org.dynamicruntime.sql.DnSqlStatement;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.SqlDatabase;
import org.dynamicruntime.sql.SqlTableUtil;
import org.dynamicruntime.sql.topic.*;
import org.dynamicruntime.startup.LogStartup;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.util.EncodeUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadata.CoreConstants.*;

/**
 * Implements node functionality and parts of it can be thought of as a *subclass* of DnCoreNodeService.
 * @see org.dynamicruntime.node.DnCoreNodeService
 */
@SuppressWarnings("WeakerAccess")
public class DnNodeService implements ServiceInitializer {
    public static final String DN_NODE_SERVICE = DnNodeService.class.getSimpleName();

    public DnCoreNodeService coreNodeService;
    public DnNodeId nodeId;
    public boolean isInit = false;

    //
    // Tables and queries
    //
    public SqlDatabase sqlDb;
    public DnTable instanceConfig;
    public DnSqlStatement iInstanceConfig;
    public DnSqlStatement qInstanceConfig;
    public DnSqlStatement qInstanceConfigByName;

    //
    // Loaded data.
    //
    public final Map<String,Object> instanceConfigData = mMap();

    public static DnNodeService get(DnCxt cxt) {
        Object obj = cxt.instanceConfig.get(DN_NODE_SERVICE);
        return (obj instanceof DnNodeService) ? (DnNodeService)obj : null;
    }

    @Override
    public String getServiceName() {
        return DN_NODE_SERVICE;
    }

    @Override
    public void onCreate(DnCxt cxt) {
        coreNodeService = DnCoreNodeService.get(cxt);
    }

    @Override
    public void checkInit(DnCxt cxt) throws DnException {
        if (isInit) {
            return;
        }
        nodeId = Objects.requireNonNull(coreNodeService).getNodeId();

        // We use topic mechanics to get access to the appropriate database instance.
        SqlTopicService topicService = SqlTopicService.get(cxt);
        // Node topic does not do standard transactions and do not have separate storage for shards.
        // This means we can just grab the topic and get a persistent reference to the database
        // instance it created or pulled in.
        SqlTopicInfo nodeTopic = new SqlTopicInfo(null, null,
                false, null );
        Objects.requireNonNull(topicService).registerTopicContainer(SqlTopicConstants.NODE_TOPIC, nodeTopic);
        SqlTopic topic = topicService.getOrCreateTopic(cxt, SqlTopicConstants.NODE_TOPIC);
        sqlDb = topic.sqlDb;
        sqlDb.withSession(cxt, (()-> {
            SqlCxt sqlCxt = new SqlCxt(cxt, topic);
            initTablesAndQueries(sqlCxt);
            initAndLoadTables(sqlCxt);
        }));
        isInit = true;
    }

    public void initTablesAndQueries(SqlCxt sqlCxt) throws DnException {
        DnCxt cxt = sqlCxt.cxt;
        instanceConfig = cxt.getSchema().getTableMustExist(DnNodeTableConstants.NT_INSTANCE_CONFIG);
        SqlTableUtil.checkCreateTable(sqlCxt, instanceConfig);
        iInstanceConfig = SqlTopicUtil.mkTableInsertStmt(sqlCxt, instanceConfig);
        qInstanceConfig = SqlTopicUtil.mkTableSelectStmt(sqlCxt, instanceConfig);
        qInstanceConfigByName = SqlTopicUtil.mkNamedTableSelectStmt(sqlCxt,
                "qByName" + instanceConfig.tableName, instanceConfig, mList(ND_INSTANCE_NAME));
    }

    public void initAndLoadTables(SqlCxt sqlCxt) throws DnException {
        DnCxt cxt = sqlCxt.cxt;
        List<Map<String,Object>> rows = sqlDb.queryDnStatement(cxt, qInstanceConfigByName, mMap(ND_INSTANCE_NAME,
                cxt.instanceConfig.instanceName));
        for (var row : rows) {
            boolean isEnabled = getBoolWithDefault(row, DnSchemaDefConstants.ENABLED, false);
            if (isEnabled) {
                String configName = getReqStr(row, ND_CONFIG_NAME);
                String configType = getReqStr(row, ND_CONFIG_TYPE);
                Map<String,Object> configData = getMapDefaultEmpty(row, ND_CONFIG_DATA);
                instanceConfigData.put(configName, configData);
                if (configType.equals(NDC_AUTH_CONFIG)) {
                    // A type of config data that we recognize.
                    DnAuthConfig authConfig = DnAuthConfig.extract(configData);
                    coreNodeService.nodeData.putAuthConfig(configName, authConfig);
                }
            }
        }
        String authLookupKey = coreNodeService.instanceAuthConfigKey;
        if (coreNodeService.nodeData.getAuthConfig(authLookupKey) == null) {
            if (instanceConfigData.containsKey(authLookupKey)) {
                throw new DnException("Encryption key information is not stored with the type " +
                        NDC_AUTH_CONFIG + ".");
            }
            insertAuthRow(cxt);
        }
    }

    public void insertAuthRow(DnCxt cxt) throws DnException {
        // Create the new encryption key.
        String encryptionKey = EncodeUtil.mkEncryptionKey();
        Map<String,Object> authRow = mMap(ND_INSTANCE_NAME, cxt.instanceConfig.instanceName,
                ND_CONFIG_TYPE, NDC_AUTH_CONFIG, ND_CONFIG_NAME, coreNodeService.instanceAuthConfigKey,
                ND_CONFIG_DATA, DnAuthConfig.mkAuthData(encryptionKey));
        DnException failedInsert = null;
        try {
            SqlTopicUtil.prepForStdExecute(cxt, authRow);
            LogStartup.log.info(cxt, "Storing shared security key into database");
            sqlDb.executeDnStatement(cxt, iInstanceConfig, authRow);

        } catch (DnException e) {
            failedInsert = e;

        }
        Map<String,Object> dbAuthRow = sqlDb.queryOneDnStatement(cxt, qInstanceConfig, authRow);
        if (dbAuthRow == null) {
            throw new DnException("Unable to insert encryption information", failedInsert);
        }
        String configType = getReqStr(dbAuthRow, ND_CONFIG_TYPE);
        if (!configType.equals(NDC_AUTH_CONFIG)) {
            throw new DnException("Insert auth config row has wrong type.");
        }
        instanceConfigData.put(cxt.instanceConfig.instanceName, dbAuthRow);
        DnAuthConfig authConfig = DnAuthConfig.extract(getMapDefaultEmpty(dbAuthRow, ND_CONFIG_DATA));
        coreNodeService.nodeData.putAuthConfig(coreNodeService.instanceAuthConfigKey, authConfig);
    }
}
