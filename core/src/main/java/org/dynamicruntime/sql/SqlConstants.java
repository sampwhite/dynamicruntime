package org.dynamicruntime.sql;

@SuppressWarnings("WeakerAccess")
public class SqlConstants {
    public final static String USER = "user";
    public final static String HOSTNAME = "hostname";
    public final static String DATABASE = "database";
    public final static String NUM_CONNECTIONS = "numConnections";
    public final static String PASSWORD_KEY = "passwordKey";
    public final static String DB_TYPE = "dbType";

    // Database types we currently support.
    public final static String POSTGRESQL = "postgresql";
    public final static String H2 = "h2";
}
