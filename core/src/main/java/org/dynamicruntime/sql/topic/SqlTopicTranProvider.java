package org.dynamicruntime.sql.topic;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.sql.*;
import org.dynamicruntime.util.DnCollectionUtil;

import java.util.Map;
import java.util.UUID;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

/**
 * Implements the details of a typical top level SQL topic transaction. This transaction performs
 * an insert on retry logic if it fails to gain a lock on its first pass. This insert is done outside
 * of a transaction (Some database, such as MySQL, do badly when performing inserts inside a transaction
 * when the tables has uniqueness constraints on some of its columns in addition to the primary key).
 * This class also handles some of the typical protocol fields such as createdDate and modifiedDate
 * and also supplies default behavior for userId and group.
 */
@SuppressWarnings("WeakerAccess")
public class SqlTopicTranProvider implements SqlTranExecProvider {
    public final SqlCxt sqlCxt;
    public final String tranName;
    public final String tranId;
    public final SqlFunction tranExecute;
    public final DnCxt cxt;
    public final SqlDatabase sqlDb;
    public final SqlTopic sqlTopic;
    public final boolean isUserTran;

    public SqlTopicTranProvider(SqlCxt sqlCxt, String tranName, String tranId, SqlFunction tranExecute) {
        this.sqlCxt = sqlCxt;
        this.tranName = tranName;
        String tId = tranId;
        if (tId == null) {
            // This is not a good idea for endpoint based requests.
            tId = tranName + UUID.randomUUID().toString();
        }
        this.tranId = tId;
        this.tranExecute = tranExecute;
        this.cxt = sqlCxt.cxt;
        this.sqlDb = sqlCxt.sqlDb;
        if (sqlCxt.sqlTopic == null || sqlCxt.sqlTopic.iTranLockQuery == null) {
            throw new RuntimeException("Sql Context object needs to provide a SQL topic with an insert query.");
        }
        this.sqlTopic = sqlCxt.sqlTopic;
        this.isUserTran = SqlTopicUtil.hasUserFields(sqlTopic.iTranLockQuery);
        if (this.isUserTran ) {
            SqlTopicUtil.checkAddUserFields(cxt, this.sqlCxt.tranData);
        }
    }

    @Override
    public void insert() throws DnException {
        if (sqlTopic.iTranLockQuery.returnGeneratedKeys) {
            throw new DnException(String.format("Cannot do insert in transaction logic because the " +
                    "insert auto increments column values for transaction %s.", tranName));
        }
        var templateData = sqlTopic.topicInfo.data;
        if (templateData != null) {
            // Under merge template data.
            for (var key : templateData.keySet()) {
                if (!sqlCxt.tranData.containsKey(key)) {
                    sqlCxt.tranData.put(key, templateData.get(key));
                }
            }
        }
        prepForExecute();
        SqlTopicUtil.prepForTranInsert(cxt, sqlCxt.tranData);
        sqlDb.executeDnStatement(cxt, this.sqlTopic.iTranLockQuery, sqlCxt.tranData);
    }

    @Override
    public boolean lock() throws DnException {
        sqlCxt.tranAlreadyDone = false;
        sqlCxt.tranData.put(TOUCHED_DATE, cxt.now());
        return sqlDb.executeDnStatement(cxt, this.sqlTopic.uTakeLockQuery, sqlCxt.tranData) > 0;
    }

    @Override
    public void execute() throws DnException {
        Map<String,Object> curRow = sqlDb.queryOneDnStatement(cxt, sqlTopic.qTranLockQuery, sqlCxt.tranData);
        if (curRow == null) {
            // Something went seriously wrong.
            throw new DnException("Data not present for " + sqlTopic.qTranLockQuery.name +
                    " after initiating transaction.");
        }

        for (var key : curRow.keySet()) {
            if (!key.equals(LAST_TRAN_ID) && !key.equals(TOUCHED_DATE)) {
                sqlCxt.tranData.put(key, curRow.get(key));
            }
        }

        // Do the actual work of the query.
        tranExecute.execute();

        if (!sqlCxt.tranAlreadyDone) {
            sqlCxt.tranData.put(LAST_TRAN_ID, tranId);

            prepForExecute();

            // Write back that we did the transaction.
            sqlDb.executeDnStatement(cxt, sqlTopic.uTranLockQuery, sqlCxt.tranData);
        }
    }

    public void prepForExecute() throws DnException {
        SqlTopicUtil.prepForStdExecute(cxt, sqlCxt.tranData);
    }

    public static void executeTopicTran(SqlCxt sqlCxt, String tranName, String tranId, Map<String,Object> tranData,
            SqlFunction tranExecute) throws DnException {
        // We are going to mutate, the *sqlCxt.tranData*. We do not want to surprise caller by having their
        // tranData parameter mutated. If they need the changed version, they can always get it from sqlCxt.
        sqlCxt.tranData = DnCollectionUtil.cloneMap(tranData);
        var provider = new SqlTopicTranProvider(sqlCxt, tranName, tranId, tranExecute);
        SqlTranUtil.doTran(sqlCxt, tranName, provider);
    }
}
