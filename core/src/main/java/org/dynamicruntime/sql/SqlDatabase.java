package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnField;
import org.dynamicruntime.sql.topic.SqlTopicInterface;
import org.dynamicruntime.util.StrUtil;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class SqlDatabase {
    /** The name of the database. Each database has a unique name within a running instance. */
    public final String dbName;
    public final Driver driver;
    public final String connectionUrl;
    public final Properties connectionProperties;
    public final Map<String,DnField> reservedFields;
    public final SqlDbOptions options;
    public final Map<String, SqlTopicInterface> topics = new ConcurrentHashMap<>();
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

    public String mkSqlTableName(DnCxt cxt, String tableName) {
        String tbName;
        if (!options.identifiersCaseSensitive) {
            tbName = StrUtil.toLowerCaseIdentifier(tableName);
            if (options.useShardInTableNames && !cxt.shard.equals(DnCxtConstants.PRIMARY)) {
                tbName = StrUtil.toLowerCaseIdentifier(cxt.shard) + "_" + tbName;
            }
            if (!options.storesLowerCaseIdentifiersInSchema) {
                tbName = tbName.toUpperCase();
            }
        } else {
            tbName = StrUtil.capitalize(tableName);
            if (options.useShardInTableNames && !cxt.shard.equals(DnCxtConstants.PRIMARY)) {
                tbName = StrUtil.capitalize(cxt.shard) + tbName;
            }
        }
        return tbName;
    }

    public void withSession(DnCxt cxt, SqlFunction function) throws DnException {
        var sqlSession = SqlSession.get(cxt);
        boolean createdIt = false;
        if (sqlSession == null) {
            createdIt = true;
            try {
                sqlSession = connections.poll(pollWaitTime, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new DnException(
                        String.format("Interrupted while waiting for SQL connection in database %s.", dbName),
                        null, DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.INTERRUPTED);
            }
            if (sqlSession == null) {
                throw new DnException(String.format("Unable to get database connection for " +
                        "database %s after waiting %d seconds.", dbName, pollWaitTime), null,
                        DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.CONNECTION);
            }
            cxt.session.put(SqlSession.SQL_SESSION, sqlSession);
            sqlSession.isBeingUsed = true;
        }
        try {
            if (createdIt) {
                 // Make sure connection is initialized (and give it a chance to throw an error).
                sqlSession.getConnection();
            }
            function.execute();
        } finally {
            if (createdIt) {
                cxt.session.remove(SqlSession.SQL_SESSION);
                sqlSession.isBeingUsed = false;
                connections.offer(sqlSession);
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

    public List<Map<String,Object>> queryDnStatement(DnCxt cxt, DnSqlStatement stmt, Map<String,Object> data)
        throws DnException {
        SqlSession sqlSession = getMustExist(cxt);
        SqlBoundStatement boundStmt = sqlSession.checkAndGetStatement(stmt);
        SqlColumnAliases aliases = boundStmt.aliases;

        var pStmt = getAndBindPreparedStatement(cxt, boundStmt, data);
        try {
            List<Map<String,Object>> retVal = mList();
            ResultSet rs = pStmt.executeQuery();
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
        } catch (SQLException e) {
            throw SqlStmtUtil.mkDnException(
                    String.format("Failure querying for result set from statement %s.", stmt.name), e);
        }
    }

    public PreparedStatement getAndBindPreparedStatement(DnCxt cxt, SqlBoundStatement boundStmt,
            Map<String,Object> data)
            throws DnException {
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
}
