package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;

/** Convenience wrapper to bundle common parameters to SQL method calls. */
public class SqlCxt {
    public final DnCxt cxt;
    public final SqlDatabase sqlDb;
    /** The topic being applied to a set of tables. In theory any topic can be split off into a separate database. */
    public final String topic;

    public SqlCxt(DnCxt cxt, SqlDatabase sqlDb, String topic) {
        this.cxt = cxt;
        this.sqlDb = sqlDb;
        this.topic = topic;
    }
}
