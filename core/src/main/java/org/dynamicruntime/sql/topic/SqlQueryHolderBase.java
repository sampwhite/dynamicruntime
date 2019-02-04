package org.dynamicruntime.sql.topic;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.SqlDatabase;


/** Used to indicate that an object holds queries. */
@SuppressWarnings("WeakerAccess")
public abstract class SqlQueryHolderBase {
    /** Name of this query holder. */
    public final String name;
    /** Back pointer to topic object that holds this object. */
    public final SqlTopic sqlTopic;
    /** The database that this holder is associated with. */
    public final SqlDatabase sqlDb;

    public SqlQueryHolderBase(String name, SqlTopic sqlTopic) {
        this.name = name;
        this.sqlTopic = sqlTopic;
        this.sqlDb = sqlTopic.sqlDb;
    }

    /** Called within an sql session. */
    abstract public void init(SqlCxt cxt) throws DnException;
}
