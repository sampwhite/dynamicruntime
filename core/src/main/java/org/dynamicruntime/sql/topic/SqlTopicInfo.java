package org.dynamicruntime.sql.topic;

import java.util.Map;

import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class SqlTopicInfo {
    /** Name of table used to do top level locking. Can be null if topic does not use standard approach
     * for locking a topic. */
    public final String tranLockTableName;

    /** Initial payload of data for creating an empty placeholder row in the transaction table. */
    public final Map<String,Object> insertTemplateData;

    /** Other parameters to the topic for custom behaviors. */
    public final Map<String,Object> data;

    /** Whether different shards must have different tables. */
    public boolean shardsHaveSeparateStorage;

    public SqlTopicInfo(String tranLockTableName, Map<String,Object> insertTemplateData,
            boolean shardsHaveSeparateStorage, Map<String,Object> data) {
        this.tranLockTableName = tranLockTableName;
        this.insertTemplateData = insertTemplateData;
        this.shardsHaveSeparateStorage = shardsHaveSeparateStorage;
        this.data = data;
    }

    /** If default behaviors are sufficient, then this is the constructor to use. */
    public SqlTopicInfo(String tranLockTableName) {
        this(tranLockTableName, mMap(), false, mMap());
    }

    public static SqlTopicInfo mkShardsInSeparateStorage(String tranLockTableName) {
        return new SqlTopicInfo(tranLockTableName, mMap(), true, mMap());
    }

}
