package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class SqlSession {
    public static final String SQL_SESSION = SqlSession.class.getSimpleName();

    public final SqlDatabase sqlDb;
    /** Set to not null when valid connection has been assigned to this object. */
    public Connection conn;
    public volatile long lastAccess;
    public volatile boolean isBeingUsed;
    public boolean inTran = false;

    public Map<String,SqlBoundStatement> preparedStatements = new HashMap<>();

    public SqlSession(SqlDatabase sqlDb) {
        this.sqlDb = sqlDb;
    }

    public static SqlSession get(DnCxt cxt) {
        var obj = cxt.session.get(SQL_SESSION);
        return (obj instanceof SqlSession) ? (SqlSession)obj : null;
    }

    /** Should be called whenever there is a connection issue when executing a query, but only when the
     * session is being released. (For example, if an exception was thrown because a query was running too long). */
    @SuppressWarnings("unused")
    public void setInvalid() {
        synchronized (this) {
            Connection c = conn;
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ignore) {}
            }
            conn = null;
        }
    }

    public Connection getConnection() {
        return conn;
    }

    public Connection getSessionStartConnection() throws DnException {
        lastAccess = System.currentTimeMillis();
        synchronized (this) {
            if (conn == null) {
                // Not clear if this is necessary.
                for (var stmt : preparedStatements.values()) {
                    try {
                        stmt.stmt.close();
                    } catch (Exception ignore) {}
                }
                preparedStatements.clear();
                conn = sqlDb.createConnection();
                // Put in any transaction isolation or connection settings here.
            }
            return conn;
        }

    }

    public SqlBoundStatement checkAndGetStatement(DnSqlStatement dnStmt) throws DnException {
        synchronized (this) {
            if (conn == null) {
                throw new DnException(
                        String.format("Getting bound statement %s when no valid connection has " +
                                "been assigned to session.", dnStmt.sessionKey));
            }
            var retVal = preparedStatements.get(dnStmt.sessionKey);
            if (retVal == null) {
                try {
                    var prepStmt = (dnStmt.returnGeneratedKeys) ?
                            conn.prepareStatement(dnStmt.sql, Statement.RETURN_GENERATED_KEYS) :
                            conn.prepareStatement(dnStmt.sql);
                    prepStmt.setQueryTimeout(sqlDb.queryTimeout);
                    var aliases = sqlDb.getAliases(dnStmt.topic);
                    var newBound = new SqlBoundStatement(dnStmt, aliases, conn, prepStmt);
                    preparedStatements.put(dnStmt.sessionKey, newBound);
                    retVal = newBound;
                } catch (SQLException e) {
                    throw new DnException(String.format("Unable to prepare statement %s with sql %s.",
                            dnStmt.name, dnStmt.sql),
                            e, DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.IO);
                }
            }
            return retVal;
        }
    }
}
