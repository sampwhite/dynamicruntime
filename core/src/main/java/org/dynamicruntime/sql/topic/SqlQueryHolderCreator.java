package org.dynamicruntime.sql.topic;

@FunctionalInterface
public interface SqlQueryHolderCreator<T extends SqlQueryHolderBase> {
    T createQueryHolder();
}
