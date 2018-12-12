package org.dynamicruntime.sql;

import org.dynamicruntime.exception.DnException;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import static org.dynamicruntime.util.DnCollectionUtil.*;

@SuppressWarnings("WeakerAccess")
public class SqlDatabase {
    /** The name of the database. Each database has a unique name within a running instance. */
    public final String dbName;
    public final Driver driver;
    public final String connectionUrl;
    public final Properties connectionProperties;
    public final SqlDbOptions options;
    public final Map<String,SqlTopicInterface> topics = new ConcurrentHashMap<>();
    private final Map<String,SqlColumnAliases> topicAliases = mMapT();

    public final ArrayBlockingQueue<SqlSession> connections;

    public SqlDatabase(String dbName, Driver driver, String connectionUrl, Properties connectionProperties,
            SqlDbOptions options, int maxConnections) {
        this.dbName = dbName;
        this.driver = driver;
        this.connectionUrl = connectionUrl;
        this.connectionProperties = connectionProperties;
        this.options = options;
        this.connections = new ArrayBlockingQueue<>(maxConnections, true);
        for (int i = 0; i < maxConnections; i++) {
            connections.add(new SqlSession(this));
        }
    }

    public Connection createConnection() throws DnException {
        try {
            return driver.connect(connectionUrl, connectionProperties);
        } catch (SQLException e) {
            throw new DnException(String.format("Could not create a connection to database ", dbName), e,
                    DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.CONNECTION);
        }
    }

    public void addAliases(String topic, Map<String,String> fieldToColumnNames) {
        synchronized (topicAliases) {
            SqlColumnAliases aliases = topicAliases.computeIfAbsent(topic,
                    (t -> new SqlColumnAliases(mMapT(), mMapT())));
            aliases.newFieldNameToColNameFeeder.putAll(fieldToColumnNames);
        }
    }

    public SqlColumnAliases getAliases(String topic) {
        synchronized (topicAliases) {
            SqlColumnAliases aliases = topicAliases.computeIfAbsent(topic, (t -> new SqlColumnAliases(mMapT(), mMapT())));
            if (aliases.newFieldNameToColNameFeeder.size() > 0) {
                SqlColumnAliases newAliases = aliases.getUpdated();
                topicAliases.put(topic, newAliases);
                aliases = newAliases;
            }
            return aliases;
        }
    }
}
