package org.dynamicruntime.sql.topic;

@FunctionalInterface
public interface SqlTopicCreator {
    SqlTopicInterface createTopic();
}
