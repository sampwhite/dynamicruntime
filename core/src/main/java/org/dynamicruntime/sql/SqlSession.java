package org.dynamicruntime.sql;

import org.dynamicruntime.exception.DnException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class SqlSession {
    public final SqlDatabase sqlDb;
    /** Set to not null when valid connection has been assigned to this object. */
    public Connection conn;
    public volatile long lastAccess;

    public Map<String,SqlBoundStatement> preparedStatements = new HashMap<>();

    public SqlSession(SqlDatabase sqlDb) {
        this.sqlDb = sqlDb;
    }

    /** Should be called whenever there is connection issue when executing a query, but only when
     * session is being released or if there is query running too long. */
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

    public Connection getConnection() throws DnException {
        lastAccess = System.currentTimeMillis();
        synchronized (this) {
            if (conn == null) {
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
                    var prepStmt = conn.prepareStatement(dnStmt.sql);
                    prepStmt.setQueryTimeout(60); // One minute has been a good value for a long time.
                    var aliases = sqlDb.getAliases(dnStmt.topic);
                    var newBound = new SqlBoundStatement(dnStmt, aliases, conn, prepStmt);
                    preparedStatements.put(dnStmt.sessionKey, newBound);
                    retVal = newBound;
                } catch (SQLException e) {
                    throw new DnException(String.format("Unable to prepare statement %s with sql %s.",
                            e, DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.IO));
                }
            }
            return retVal;
        }
    }
}
