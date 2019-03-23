package org.dynamicruntime.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;

/** The object used to perform execution of a database query. It does not have the data that are fed in as
 * parameters to the PreparedStatement, that will be supplied at execution time. */
@SuppressWarnings("WeakerAccess")
public class SqlBoundStatement {
    public final DnSqlStatement dnSql;
    public final SqlColumnAliases aliases;
    public final Connection conn;
    public final PreparedStatement stmt;

    public SqlBoundStatement(DnSqlStatement dnSql, SqlColumnAliases aliases, Connection conn,
            PreparedStatement stmt) {
        this.aliases = aliases;
        this.dnSql = dnSql;
        this.conn = conn;
        this.stmt = stmt;
    }
}
