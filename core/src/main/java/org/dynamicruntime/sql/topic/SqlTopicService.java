package org.dynamicruntime.sql.topic;

import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.SqlDatabase;
import org.dynamicruntime.sql.SqlDbBuilder;
import org.dynamicruntime.startup.StartupServiceInitializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("WeakerAccess")
public class SqlTopicService implements StartupServiceInitializer {
    public static final String SQL_TOPIC_SERVICE = SqlTopicService.class.getSimpleName();

    public final Map<String,SqlTopicHolder> topics = new ConcurrentHashMap<>();
    public final Map<String,SqlDatabase> databases = new ConcurrentHashMap<>();
    public boolean isInMemory;

    public static SqlTopicService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(SQL_TOPIC_SERVICE);
        return (obj instanceof SqlTopicService) ? (SqlTopicService)obj : null;
    }

    public static SqlCxt mkSqlCxt(DnCxt cxt, String topic) throws DnException {
        var s = get(cxt);
        if (s == null) {
            throw new DnException(String.format("Could not create Sql Context for topic %s.", topic));
        }
        var sqlTopic = s.getOrCreateTopic(cxt, topic);
        return new SqlCxt(cxt, sqlTopic);
    }

    @Override
    public String getServiceName() {
        return SQL_TOPIC_SERVICE;
    }

    @Override
    public void checkInit(DnCxt cxt) {
        isInMemory = SqlTopicUtil.isInMemoryDb(cxt);
    }

    /** Topics must be registered in order to be used and they should be registered at startup. */
    public void registerTopicContainer(String topicName, SqlTopicInfo topicInfo) {
        topics.put(topicName, new SqlTopicHolder(topicName, topicInfo, isInMemory));
    }

    /**
     * Gets or creates an SqlTopic object. If the object needs to be created, then
     * the method hunts through configuration. A full configuration looks like this:
     *
     * dbTopic.{topicName}.shardGroup = {nameOfShardGroup}
     * dbShardGroup.{groupName}.shards.{shardName}.dbName = {dbName}
     * db.{dbName} = {... db connection information ...}
     *
     * However, if the *shardGroup* entry is not present, then the entry for the dbTopic can optionally look like
     * this:
     *
     * dbTopic.{topicName}.dbName = {dbName}
     * db.{dbName} = {... db connection information ...}
     */
    public SqlTopic getOrCreateTopic(DnCxt cxt, String topic) throws DnException {
        SqlTopicHolder holder = topics.get(topic);
        if (holder == null) {
            return null;
        }

        synchronized (databases) {
            SqlTopic sqlTopic = holder.topicsByShard.get(cxt.shard);
            if (sqlTopic == null) {
                sqlTopic = createSqlTopic(cxt, holder);
                SqlCxt sqlCxt = new SqlCxt(cxt, sqlTopic);
                sqlTopic.init(sqlCxt);
                holder.topicsByShard.put(cxt.shard, sqlTopic);
            }
            return sqlTopic;
       }
    }

    /** Creates a topic, should only be called in synchronization of the databases. */
    public SqlTopic createSqlTopic(DnCxt cxt, SqlTopicHolder holder) throws DnException {
        boolean isInMemory = holder.isInMemory;
        String topic = holder.topicName;
        // No configuration needed if in memory.
        if (isInMemory) {
            String memKey = "mem:" + cxt.shard;
            SqlDatabase db = databases.get(memKey);
            if (db == null) {
                db = createDbConnection(cxt, memKey, true);
                databases.put(memKey, db);
            }
            return new SqlTopic(holder.topicName, db, holder.topicInfo);
        }
        // The main case.

        // Do the configuration hunt.
       String baseKey = "dbTopic." + topic;

        // First try finding a shard group.
        String shardNameKey = baseKey + ".shardGroup";
        String shardEntryName =  DnConfigUtil.getConfigString(cxt, shardNameKey,
        null, String.format("Database shard group name for topic %s.", topic));
        String groupDbName = null;
        if (shardEntryName != null) {
            String shardDbNameKey = "dbShardGroup." + shardEntryName + ".shards." + cxt.shard + ".dbName";
            groupDbName = DnConfigUtil.getConfigString(cxt, shardDbNameKey, null,
                    String.format("Database name for shard group and shard for topic %s", holder.topicName));
        }
        String dbName = "primary";
        if (groupDbName == null) {
            dbName = DnConfigUtil.getConfigString(cxt, baseKey + ".dbName", dbName,
                    String.format("Database used for topic %s.", topic));
        } else {
            dbName = groupDbName;
        }
        SqlDatabase db = databases.get(dbName);
        if (db == null) {
            db = createDbConnection(cxt, dbName, false);
            databases.put(dbName, db);
        }

        boolean shardsShareDatabase = groupDbName == null;
        return new SqlTopic(holder.topicName, db, holder.topicInfo, shardsShareDatabase);
    }

    public SqlDatabase createDbConnection(DnCxt cxt, String dbName, boolean isInMemory) throws DnException {
        var builder = new SqlDbBuilder(cxt, dbName, isInMemory);
        return builder.createDatabase();
    }


}
