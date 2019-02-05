package org.dynamicruntime.sql.topic;

import org.dynamicruntime.config.ConfigConstants;
import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnField;
import org.dynamicruntime.schemadef.DnTable;
import org.dynamicruntime.sql.DnSqlStatement;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.SqlStmtUtil;
import org.dynamicruntime.util.ParsingUtil;

import java.util.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;

@SuppressWarnings("WeakerAccess")
public class SqlTopicUtil {
    /** Fields in row that do not indicate real change if they are different. */
    public static final Set<String> ROW_AUDIT_FIELDS = Set.of(CREATED_DATE, MODIFIED_DATE, TOUCHED_DATE);
    public static boolean isInMemoryDb(DnCxt cxt) {
        return DnConfigUtil.getConfigBool(cxt, ConfigConstants.IN_MEMORY_SIMULATION, false,
                "Whether code is using H2 in-memory databases to run a simulated version of " +
                        "the application.");
    }

    public static DnSqlStatement mkTableInsertStmt(SqlCxt sqlCxt, DnTable table) {
        String qName = "i" + table.tableName;
        boolean[] hasAutoIncrement = {false};
        String query = SqlStmtUtil.mkInsertQuery(table.tableName, table.columns, hasAutoIncrement);
        var stmt = SqlStmtUtil.prepareSql(sqlCxt, qName, table.columns, query);
        if (hasAutoIncrement[0]) {
            stmt.returnGeneratedKeys = true;
        }
        return stmt;
    }

    public static DnSqlStatement mkTableSelectStmt(SqlCxt sqlCxt, DnTable table) {
        String qName = "q" + table.tableName;
        return mkNamedTableSelectStmt(sqlCxt, qName, table, table.primaryKey.fieldNames);
    }

    public static DnSqlStatement mkNamedTableSelectStmt(SqlCxt sqlCxt, String qName, DnTable table,
            List<String> andFields) {
        String query = SqlStmtUtil.mkSelectQuery(table.tableName, andFields);
        return SqlStmtUtil.prepareSql(sqlCxt, qName, table.columns, query);
    }

    public static DnSqlStatement mkTableUpdateStmt(SqlCxt sqlCxt, DnTable table) {
        String qName = "u" + table.tableName;
        List<DnField> relevantColumns = nMapSimple(table.columns, (col ->
                (!col.name.equals(TOUCHED_DATE) && !col.isAutoIncrementing() && !col.name.equals(CREATED_DATE)) ?
                        col : null));
        String query = SqlStmtUtil.mkUpdateQuery(table.tableName, relevantColumns, table.primaryKey.fieldNames);
        return SqlStmtUtil.prepareSql(sqlCxt, qName, table.columns, query);
    }

    public static DnSqlStatement mkTableTranLockStmt(SqlCxt sqlCxt, DnTable table) throws DnException {
        if (!table.columnsByName.containsKey(TOUCHED_DATE)) {
            throw DnException.mkConv(String.format("Table %s cannot have a touch query created for it because it " +
                    "does not have the column *%s*.", table.tableName, TOUCHED_DATE));
        }
        List<String> andFields = table.primaryKey.fieldNames;
        List<DnField> relevantColumns = nMap(table.columns, (col ->
                (andFields.contains(col.name) || col.name.equals(TOUCHED_DATE)) ? col : null));
        String qName = "uTran" + table.tableName;
        String query = SqlStmtUtil.mkUpdateQuery(table.tableName, relevantColumns, table.primaryKey.fieldNames);
        return SqlStmtUtil.prepareSql(sqlCxt, qName, table.columns, query);
    }

    @SuppressWarnings("unused")
    public boolean areRowsDifferent(Map<String,Object> row1, Map<String,Object> row2) {
        Map<String,Object> notConsumed = cloneMap(row2);
        for (String key : row1.keySet()) {
            if (!ROW_AUDIT_FIELDS.contains(key)) {
                if (!ParsingUtil.isJsonEqual(row1.get(key), row2.get(key))) {
                    return false;
                }
                notConsumed.remove(key);
            }
        }
        for (String key : notConsumed.keySet()) {
            if (!ROW_AUDIT_FIELDS.contains(key) && row2.get(key) != null) {
                return false;
            }
        }
        return true;
    }

    public static void checkAddUserFields(DnCxt cxt, Map<String,Object> rowValues) {
        long userId = cxt.userProfile != null ? cxt.userProfile.userId : DnCxtConstants.AC_SYSTEM_USER_ID;
        String group = cxt.userProfile != null ? cxt.userProfile.userGroup : DnCxtConstants.AC_LOCAL;
        if (!rowValues.containsKey(USER_ID)) {
            rowValues.put(USER_ID, userId);
            rowValues.put(USER_GROUP, group);
        }
        if (!rowValues.containsKey(USER_GROUP)) {
            rowValues.put(USER_GROUP, group);
        }
    }

    public static void prepForTranInsert(DnCxt cxt, Map<String,Object> data) {
        data.put(TOUCHED_DATE, cxt.now());
        data.put(LAST_TRAN_ID, "INITIAL_INSERT");
    }


    /** Adds standard protocol date fields to the row data about to be executed on. This call
     * modifies the contents of *rowValues*. */
    public static void prepForStdExecute(DnCxt cxt, Map<String,Object> rowValues) throws DnException {
        prepForDatesExecute(cxt, rowValues);
        rowValues.put(ENABLED, true);
    }

    public static void prepForDatesExecute(DnCxt cxt, Map<String,Object> rowValues) throws DnException {
        Date curCreatedDate = getOptDate(rowValues, CREATED_DATE);
        Date now = cxt.now();
        if (curCreatedDate == null) {
            rowValues.put(CREATED_DATE, now);
        }
        Date curLastUpdated = getOptDate(rowValues, MODIFIED_DATE);
        if (curLastUpdated != null) {
            // Make sure we are advancing by at least one millisecond.
            long l = curLastUpdated.getTime();
            long n = now.getTime();
            if (n >= l - 2000 && n <= l) {
                // Not advancing by at least one millisecond AND within two seconds of prior date,
                // we may either be executing queries too quickly or have node date synchronization issues.
                now = new Date(l + 1);
            }
        }
        rowValues.put(MODIFIED_DATE, now);
    }
}
