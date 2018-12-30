package org.dynamicruntime.sql.topic;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.sql.*;

import java.util.Map;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

/**
 * Implements the details of a typical top level SQL topic transaction.
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
        this.tranId = tranId;
        this.tranExecute = tranExecute;
        this.cxt = sqlCxt.cxt;
        this.sqlDb = sqlCxt.sqlDb;
        if (sqlCxt.sqlTopic == null || sqlCxt.sqlTopic.iTranLockQuery == null) {
            throw new RuntimeException("Sql Context object needs to provide a SQL topic with an insert query.");
        }
        this.sqlTopic = sqlCxt.sqlTopic;
        this.isUserTran = sqlTopic.iTranLockQuery.fields.containsKey(USER_ID);
        if (this.isUserTran) {
            if (cxt.userProfile != null && cxt.userProfile.userId > 0) {
                sqlCxt.tranData.put(USER_ID, cxt.userProfile.userId);
            }
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
        if (isUserTran) {
            SqlTopicUtil.prepForUserExecute(cxt, sqlCxt.tranData);
        } else {
            SqlTopicUtil.prepForStdExecute(cxt, sqlCxt.tranData);
        }
    }

    public static void executeTopicTran(SqlCxt sqlCxt, String tranName, String tranId, Map<String,Object> tranData,
            SqlFunction tranExecute) throws DnException {
        sqlCxt.tranData = tranData;
        var provider = new SqlTopicTranProvider(sqlCxt, tranName, tranId, tranExecute);
        SqlTranUtil.doTran(sqlCxt, tranName, provider);
    }
}
