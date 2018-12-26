package org.dynamicruntime.sql;

import org.dynamicruntime.schemadef.DnField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnSqlStatement {
    /** Shard */
    public final String shard;
    /** Topic. */
    public final String topic;
    /** Unique name within topic. */
    public final String name;
    /** Session key, combines shard, topic and name. */
    public final String sessionKey;
    /** The original SQL before entity substitutions. */
    public final String originalSql;
    /** The query to execute that needs to be prepared. */
    public final String sql;
    /** Definitions of fields being used. */
    public final Map<String, DnField> fields;
    public final String[] bindFields;
    public boolean returnGeneratedKeys;

    public DnSqlStatement(String shard, String topic, String name, String originalSql, String sql,
            List<DnField> fields, List<String> bindFields) {
        this.shard = shard;
        this.topic = topic;
        this.name = name;
        this.sessionKey = name + "@" + shard + ":" + topic;
        this.originalSql = originalSql;
        this.sql = sql;
        this.fields = new HashMap<>();
        for (var field : fields) {
             this.fields.put(field.name, field);
        }
        this.bindFields = bindFields.toArray(new String[0]);
    }

}
