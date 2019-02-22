package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.sql.topic.SqlQueryHolderBase;
import org.dynamicruntime.sql.topic.SqlQueryHolderCreator;
import org.dynamicruntime.sql.topic.SqlTopic;

import java.util.Map;

/** Convenience wrapper to bundle common parameters to SQL method calls. The wrapper also locks down the
 * active SqlTopic and shard. */
@SuppressWarnings("WeakerAccess")
public class SqlCxt {
    public final DnCxt cxt;
    public final SqlDatabase sqlDb;
    /** The topic being applied to a set of tables. In theory any topic can be split off into a separate database. */
    public final String topic;

    public final SqlTopic sqlTopic;

    /** Set during a topic transaction. This data should be set before the start of a transaction. At the
     * start of the transaction it is used to help during a row insert. Later, it can be updated
     * during the actual transaction with data that should be saved in the transaction lock row. If during
     * the transaction, the content of the row is retrieved, then this data is replaced with the data from
     * that row. */
    public Map<String,Object> tranData;

    /** Set to true if current transaction did an insert. */
    public boolean didInsert;

    /** Set to true, if transaction already done. */
    public boolean tranAlreadyDone;

    public SqlCxt(DnCxt cxt, SqlDatabase sqlDb, String topic) {
        this.cxt = cxt;
        this.sqlDb = sqlDb;
        this.topic = topic;
        this.sqlTopic = null;
    }

    public SqlCxt(DnCxt cxt, SqlTopic sqlTopic) {
        this.cxt = cxt;
        this.sqlDb = sqlTopic.sqlDb;
        this.topic = sqlTopic.name;
        this.sqlTopic = sqlTopic;
    }

    public <T extends SqlQueryHolderBase> T getOrCreateQueryHolder(String name, SqlQueryHolderCreator<T> creator)
            throws DnException {
        if (sqlTopic == null) {
            throw new DnException("Cannot create query holder " + name + " unless full topic for topic " + topic +
                    " is used.");
        }
        return sqlTopic.getOrCreateHolder(this, name, creator);
    }

    public boolean shardTablesGetDifferentNames() {
        if (sqlTopic == null) {
            return false;
        }
        return sqlTopic.shardsShareDatabase && sqlTopic.topicInfo.shardsHaveSeparateStorage;
    }
}
