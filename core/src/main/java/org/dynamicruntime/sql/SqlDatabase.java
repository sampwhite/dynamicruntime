package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnField;
import org.dynamicruntime.util.ConvertUtil;
import org.dynamicruntime.util.StrUtil;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

@SuppressWarnings("WeakerAccess")
public class SqlDatabase {
    /** The name of the database. Each database has a unique name within a running instance. */
    public final String dbName;
    public final Driver driver;
    public final String connectionUrl;
    public final Properties connectionProperties;
    public final Map<String,DnField> reservedFields;
    public final Set<String> createdTables = new HashSet<>();
    public final SqlDbOptions options;
    private final Map<String,SqlColumnAliases> topicAliases = mMapT();

    public final ArrayBlockingQueue<SqlSession> connections;
    public boolean isDebug = false;

    /** If these timeout values need to be configurable, add additional methods to this class. */
    // One minute has been a good value for a long time for query timeout. Sometimes there is a belief that the
    // timeout can be tuned to a lower value, but eventually the value gets put back to one minute.
    public int queryTimeout = 60; // Number of seconds for timeout of a query.
    // Poll timeout can be short because if the session pool is getting starved, you are probably in a death
    // spiral already and there is no point delaying the inevitable failures.
    public int pollWaitTime = 2; // Number of seconds to wait for a connection to be available.

    public SqlDatabase(String dbName, Driver driver, String connectionUrl, Properties connectionProperties,
            Map<String,DnField> reservedFields, SqlDbOptions options, int maxConnections) {
        this.dbName = dbName;
        this.driver = driver;
        this.connectionUrl = connectionUrl;
        this.connectionProperties = connectionProperties;
        this.reservedFields = reservedFields;
        this.options = options;
        this.connections = new ArrayBlockingQueue<>(maxConnections, true);
        for (int i = 0; i < maxConnections; i++) {
            connections.add(new SqlSession(this));
        }
    }

    public Connection createConnection() throws DnException {
        try {
            return driver.connect(connectionUrl, connectionProperties);
        } catch (SQLException e) {
            throw new DnException(String.format("Could not create a connection to database %s.", dbName), e,
                    DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.CONNECTION);
        }
    }

    public void addAliases(String topic, Map<String,String> fieldToColumnNames) {
        synchronized (topicAliases) {
            boolean convertUpperToLower = !options.storesLowerCaseIdentifiersInSchema &&
                    !options.identifiersCaseSensitive;
            SqlColumnAliases aliases = topicAliases.computeIfAbsent(topic,
                    (t -> new SqlColumnAliases(convertUpperToLower, reservedFields, mMapT(), mMapT())));
            aliases.newFieldNameToColNameFeeder.putAll(fieldToColumnNames);
        }
    }

    public SqlColumnAliases getAliases(String topic) {
        synchronized (topicAliases) {
            boolean convertUpperToLower = !options.storesLowerCaseIdentifiersInSchema &&
                    !options.identifiersCaseSensitive;
            SqlColumnAliases aliases = topicAliases.computeIfAbsent(topic,
                    (t -> new SqlColumnAliases(convertUpperToLower, reservedFields, mMapT(), mMapT())));
            if (aliases.newFieldNameToColNameFeeder.size() > 0) {
                SqlColumnAliases newAliases = aliases.getUpdated();
                topicAliases.put(topic, newAliases);
                aliases = newAliases;
            }
            return aliases;
        }
    }

    public void addDefaultAliases(String topic, List<DnField> fields) {
        if (options.identifiersCaseSensitive) {
            return;
        }
        Map<String,String> fldToCol = mMapT();
        for (var fld : fields) {
            String alias = StrUtil.toLowerCaseIdentifier(fld.name);
            fldToCol.put(fld.name, alias);
        }
        addAliases(topic, fldToCol);
    }

    public String mkSqlTableName(SqlCxt sqlCxt, String tableName) {
        String tbName;
        var cxt = sqlCxt.cxt;
        if (!options.identifiersCaseSensitive) {
            tbName = StrUtil.toLowerCaseIdentifier(tableName);
            if (sqlCxt.shardTablesGetDifferentNames() && !cxt.shard.equals(DnCxtConstants.PRIMARY)) {
                tbName = StrUtil.toLowerCaseIdentifier(cxt.shard) + "_" + tbName;
            }
            if (!options.storesLowerCaseIdentifiersInSchema) {
                tbName = tbName.toUpperCase();
            }
        } else {
            tbName = StrUtil.capitalize(tableName);
            if (sqlCxt.shardTablesGetDifferentNames() && !cxt.shard.equals(DnCxtConstants.PRIMARY)) {
                tbName = StrUtil.capitalize(cxt.shard) + tbName;
            }
        }
        return tbName;
    }


    public void withSession(DnCxt cxt, SqlFunction function) throws DnException {
        var sqlSession = SqlSession.get(cxt);
        boolean assignedIt = false;
        if (sqlSession == null) {
            try {
                sqlSession = connections.poll(pollWaitTime, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new DnException(
                        String.format("Interrupted while waiting for SQL connection in database %s.", dbName),
                        e, DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.INTERRUPTED);
            }
            if (sqlSession == null) {
                throw new DnException(String.format("Unable to get database connection for " +
                        "database %s after waiting %d seconds.", dbName, pollWaitTime), null,
                        DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.CONNECTION);
            }
            assignedIt = true;
            cxt.session.put(SqlSession.SQL_SESSION, sqlSession);
            sqlSession.isBeingUsed = true;
        }
        try {
            if (assignedIt) {
                 // Make sure connection is initialized (and give it a chance to throw an error).
                sqlSession.getSessionStartConnection();
            }
            function.execute();
        } catch (DnException e) {
            if (e.source.equals(DnException.DATABASE) && (e.activity.equals(DnException.IO) ||
                    e.activity.equals(DnException.CONNECTION))) {
                // At some point, may want to set more than this session invalid.
                sqlSession.setInvalid();
            }
            throw e;
        } finally {
            if (assignedIt) {
                cxt.session.remove(SqlSession.SQL_SESSION);
                sqlSession.isBeingUsed = false;
                connections.offer(sqlSession);
            }
        }
    }

    public void withTran(DnCxt cxt, SqlFunction function) throws DnException {
        var sqlSession = SqlSession.get(cxt);
        if (sqlSession != null) {
            withTranAndSession(cxt, sqlSession, function);
        } else {
            withSession(cxt, () -> {
                var ss = getMustExist(cxt);
                withTranAndSession(cxt, ss, function);
            });
        }
    }

    public void withTranAndSession(DnCxt cxt, SqlSession session, SqlFunction function) throws DnException {
        if (session.inTran) {
            function.execute();
            return;
        }
        boolean committedIt = false;
        try {
            session.conn.setAutoCommit(false);
            session.inTran = true;
            function.execute();
            session.conn.commit();
            session.conn.setAutoCommit(true);
            committedIt = true;
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException("Could not execute transaction.", e);
        } finally {
            session.inTran = false;
            if (!committedIt) {
                try {
                    session.conn.setAutoCommit(true);
                    session.conn.rollback();
                } catch (Exception ignore) {}
            }
        }
    }

    //
    // Raw query execution.
    //

    public void executeSchemaChangeSql(DnCxt cxt, String sql) throws DnException {
        // Magic resource management *try* statement from Java 1.7 that IntelliJ suggested (stmt will get closed
        // automatically).
        try (var stmt = getStatement(cxt)) {
            LogSql.log.debug(cxt, "SQL Schema change: " + sql);
            stmt.execute(sql);
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(String.format("Failed to change schema on %s with %s.",
                    dbName, sql), e);
        }
    }

    @SuppressWarnings("unused")
    public int executeSql(DnCxt cxt, String sql) throws DnException {
        try (var stmt = getStatement(cxt)) {
            if (isDebug) {
                LogSql.log.debug(cxt, "SQL Execute: " + sql);
            }
            return stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(
                    String.format("Failed to execute %s against database %s.", sql, dbName), e);
        }
    }

    @SuppressWarnings("unused")
    public ResultSet querySql(DnCxt cxt, String sql) throws DnException {
       try (var stmt = getStatement(cxt)) {
            if (isDebug) {
                LogSql.log.debug(cxt, "SQL Query: " + sql);
            }
            return stmt.executeQuery(sql);
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(String.format("Failed to query database %s with %s.", dbName, sql), e);
        }

    }
    /** Get a statement that can do raw queries. */
    public Statement getStatement(DnCxt cxt) throws DnException {
        SqlSession sqlSession = getMustExist(cxt);
        var conn = sqlSession.getConnection();
        try {
            var stmt = conn.createStatement();
            stmt.setQueryTimeout(queryTimeout);
            return stmt;
        } catch (SQLException e) {
            throw new DnException(String.format("Cannot get connection to database %s.", dbName),
                    e, DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.CONNECTION);
        }
    }

    //
    // DnSqlStatement querying.
    //

    /** Does a simple insert or update. */
    public int executeDnStatement(DnCxt cxt, DnSqlStatement stmt, Map<String,Object> data) throws DnException {
        SqlSession sqlSession = getMustExist(cxt);
        SqlBoundStatement boundStmt = sqlSession.checkAndGetStatement(stmt);
        var pStmt = getAndBindPreparedStatement(cxt, boundStmt, data);
        try {
            return pStmt.executeUpdate();
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(String.format("Could not execute query %s", stmt.name), e);
        }
    }

    /** Does an insert but allow getting back the counter Id value that was generated. */
    public int executeDnStatementGetCounterBack(DnCxt cxt, DnSqlStatement stmt, Map<String,Object> data,
            long[] counterValue) throws DnException {
        SqlSession sqlSession = getMustExist(cxt);
        SqlBoundStatement boundStmt = sqlSession.checkAndGetStatement(stmt);
        var pStmt = getAndBindPreparedStatement(cxt, boundStmt, data);
        try {
            int result = pStmt.executeUpdate();
            if (result > 0 && counterValue != null) {
                try (var generatedKeys = pStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int colCount = generatedKeys.getMetaData().getColumnCount();
                        for (int i = 0; i < colCount && i < counterValue.length; i++) {
                            counterValue[i] = generatedKeys.getLong(i + 1);
                        }
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(String.format("Could not execute query %s", stmt.name), e);
        }

    }

    /** General mechanism to query for rows. */
    public List<Map<String,Object>> queryDnStatement(DnCxt cxt, DnSqlStatement stmt, Map<String,Object> data)
            throws DnException {
        SqlSession sqlSession = getMustExist(cxt);
        SqlBoundStatement boundStmt = sqlSession.checkAndGetStatement(stmt);
        SqlColumnAliases aliases = boundStmt.aliases;

        var pStmt = getAndBindPreparedStatement(cxt, boundStmt, data);
        try {
            try (ResultSet rs = pStmt.executeQuery()) {
                List<Map<String,Object>> retVal = mList();
                //ResultSet rs = pStmt.executeQuery();
                ResultSetMetaData md = rs.getMetaData();
                List<DnField> fields = mList();
                for (int i = 0; i < md.getColumnCount(); i++) {
                    String colName = md.getColumnName(i + 1);
                    String fldName = aliases.getFieldName(colName);
                    DnField fld = SqlStmtUtil.getDnField(stmt, boundStmt.aliases, fldName);
                    fields.add(fld);
                }
                int count = 0;
                while (rs.next() && count++ < 100000) {
                    // Use linked hash map to preserve original field order of table.
                    var row = new LinkedHashMap<String,Object>();
                    for (int i = 0; i < fields.size(); i++) {
                        DnField fld = fields.get(i);
                        Object dbObj = rs.getObject(i + 1);
                        Object obj = SqlTypeUtil.convertDbObject(cxt, fld, dbObj);
                        if (obj != null) {
                            row.put(fld.name, obj);
                        }
                    }
                    retVal.add(row);
                }
                return retVal;
            }
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(
                    String.format("Failure querying for result set from statement %s.", stmt.name), e);
        }
    }

    /** Queries and returns only the first row. This is best for existence tests and when targeting
     * indexes that have a uniqueness constraint. */
    public Map<String,Object> queryOneDnStatement(DnCxt cxt, DnSqlStatement stmt, Map<String,Object> data)
            throws DnException {
        List<Map<String,Object>> values = queryDnStatement(cxt, stmt, data);
        if (values != null && values.size() > 0) {
            return values.get(0);
        }
        return null;
    }

    /** Queries a single row but only returns it if it enabled. Works well outside of transactions, not
     * so well inside transactions. Should only be done against indexes with uniqueness constraints. */
    public Map<String,Object> queryOneEnabled(DnCxt cxt, DnSqlStatement stmt, Map<String,Object> data)
            throws DnException {
        var retVal = queryOneDnStatement(cxt, stmt, data);
        return (retVal != null && ConvertUtil.getBoolWithDefault(retVal, ENABLED, false)) ?
                retVal : null;
    }

    public PreparedStatement getAndBindPreparedStatement(DnCxt cxt, SqlBoundStatement boundStmt,
            Map<String,Object> data) throws DnException {
        var dnSqlStmt = boundStmt.dnSql;
        PreparedStatement pStmt = boundStmt.stmt;
        for (int i = 0; i < dnSqlStmt.bindFields.length; i++) {
            String bindParam = dnSqlStmt.bindFields[i];
            Object obj = data.get(bindParam);
            DnField fld = SqlStmtUtil.getDnField(dnSqlStmt, boundStmt.aliases, bindParam);
            SqlTypeUtil.setStmtParameter(cxt, i + 1, dnSqlStmt.name, pStmt, fld, obj);
        }
        return pStmt;
    }

    public SqlSession getMustExist(DnCxt cxt) throws DnException {
        var sqlSession = SqlSession.get(cxt);
        if (sqlSession == null) {
            throw new DnException(String.format("Database activity can only be executed inside a " +
                    "session on database %s.", dbName));
        }
        return sqlSession;
    }

    public boolean hasCreatedTable(SqlCxt sqlCxt, String tableName) {
        String dbTableName = mkSqlTableName(sqlCxt, tableName);
        synchronized (createdTables) {
            return createdTables.contains(dbTableName);
        }
    }

    public void registerHasCreatedSqlTable(String tableName) {
        synchronized (createdTables) {
            createdTables.add(tableName);
        }
    }
}
