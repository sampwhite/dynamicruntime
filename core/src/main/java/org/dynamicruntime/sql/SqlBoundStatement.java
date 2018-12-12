package org.dynamicruntime.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;

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
