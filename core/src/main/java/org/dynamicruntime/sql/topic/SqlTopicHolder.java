package org.dynamicruntime.sql.topic;

import org.dynamicruntime.context.DnCxt;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class SqlTopicHolder {
    public final String topicName;
    public final SqlTopicInfo topicInfo;
    public final boolean isInMemory;
    public final Map<String,SqlTopic> topicsByShard = new HashMap<>();

    public SqlTopicHolder(String topicName, SqlTopicInfo topicInfo, boolean isInMemory) {
        this.topicName = topicName;
        this.topicInfo = topicInfo;
        this.isInMemory = isInMemory;
    }
}
