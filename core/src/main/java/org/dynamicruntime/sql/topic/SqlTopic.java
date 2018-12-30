package org.dynamicruntime.sql.topic;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnTable;
import org.dynamicruntime.sql.DnSqlStatement;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.SqlDatabase;
import org.dynamicruntime.sql.SqlTableUtil;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The container object that holds code to create and update tables and provide queries for execution. There is
 * one instance of a topic per topic name and shard. */
@SuppressWarnings("WeakerAccess")
public class SqlTopic {
    /** Name of topic. */
    public final String name;
    /** Database we are accessing. */
    public final SqlDatabase sqlDb;

    /** Info specific to this topic. */
    public final SqlTopicInfo topicInfo;

    /** Whether shards share the database. */
    public boolean shardsShareDatabase;

    /** Top level tran table definition. */
    public DnTable table;
    /** Inserts into top level tran locking table. This is usually done outside a transaction and only
     * after an attempt using (uTakeLockQuery) fails. After the insertion, the transaction is started
     * again using the *uTakeLockQuery*. In some cases, if rows are being found by other than the primary
     * key, custom logic to do the querying for a row and insertion should be done outside a transaction.
     * Such logic should have at least one retry loop. Note, that this may end put leaving behind placeholder
     * rows that never get filled out, but it is worth the extra smoothness it adds to the overall transaction. */
    public DnSqlStatement iTranLockQuery;
    /** Queries from top level tran query. Not used to initiate lock, but done immediately after successfully
     * taking a lock. */
    public DnSqlStatement qTranLockQuery;
    /** Updates the tran lock table. Performed only if transaction actually modified state. */
    public DnSqlStatement uTranLockQuery;
    /** Takes a lock by updating *touchedDate* in table. Taking a transaction lock by doing an
     * update instead of using *select for update* has certain advantages when using a load balance
     * cluster for your database. It also has advantages for auditing because the *touchedDate* can
     * tell you when a transaction was last performed successfully. If using a counter for the primary key,
     * then the update query where clause is based on the first index with a uniqueness constraint. */
    public DnSqlStatement uTakeLockQuery;

    /** Holds table definitions and queries. */
    public final Map<String,SqlQueryHolderBase> queryHolders = new ConcurrentHashMap<>();

    public SqlTopic(String name, SqlDatabase sqlDb, SqlTopicInfo topicInfo) {
        this(name, sqlDb, topicInfo, false);
    }

    public SqlTopic(String name, SqlDatabase sqlDb, SqlTopicInfo topicInfo, boolean shardsShareDatabase) {
        this.name = name;
        this.sqlDb = sqlDb;
        this.topicInfo = topicInfo;
        this.shardsShareDatabase = shardsShareDatabase;
    }

    public static SqlTopic get(DnCxt cxt, String topic) throws DnException {
        var topicService = SqlTopicService.get(cxt);
        return topicService != null ? topicService.getOrCreateTopic(cxt, topic) : null;
    }

    public void init(SqlCxt sqlCxt) throws DnException {
        if (topicInfo == null || topicInfo.tranLockTableName == null) {
            return;
        }
        DnCxt cxt = sqlCxt.cxt;
        String tbName = topicInfo.tranLockTableName;
        table = cxt.getSchema().getTableMustExist(tbName);

        // Create the table.
        sqlDb.withSession(cxt, () -> SqlTableUtil.checkCreateTable(sqlCxt, table));

        // Create the queries.
        iTranLockQuery = SqlTopicUtil.mkTableInsertStmt(sqlCxt, table);
        qTranLockQuery = SqlTopicUtil.mkTableSelectStmt(sqlCxt, table);
        uTranLockQuery = SqlTopicUtil.mkTableUpdateStmt(sqlCxt, table);
        uTakeLockQuery = SqlTopicUtil.mkTableTranLockStmt(sqlCxt, table);
    }

    @SuppressWarnings("unchecked")
    public <T extends SqlQueryHolderBase> T getOrCreateHolder(SqlCxt sqlCxt, String holderName,
            SqlQueryHolderCreator<T> creator) throws DnException {
        T queryHolder = (T)queryHolders.get(holderName);
        if (queryHolder == null) {
            synchronized(queryHolders) {
                queryHolder = (T)queryHolders.get(holderName);
                if (queryHolder == null) {
                    queryHolder = creator.createQueryHolder();
                    var qt = queryHolder; // To make lambda happy.
                    sqlCxt.sqlDb.withSession(sqlCxt.cxt, () -> qt.init(sqlCxt));
                    queryHolders.put(holderName, queryHolder);
                }
            }
        }
        return queryHolder;
    }
}
