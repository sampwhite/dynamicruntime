package org.dynamicruntime.sql;

@FunctionalInterface
public interface SqlTopicCreator {
    SqlTopicInterface createTopic();
}
