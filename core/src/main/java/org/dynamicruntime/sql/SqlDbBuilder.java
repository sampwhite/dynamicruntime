package org.dynamicruntime.sql;

import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnField;
import org.dynamicruntime.util.ConvertUtil;

import static org.dynamicruntime.sql.SqlConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/** Builds up the configuration necessary to create an {@link SqlDatabase} instance. In particular, it
 * implements scoped configuration where configuration can be scoped under a particular database name. */
@SuppressWarnings("WeakerAccess")
public class SqlDbBuilder {
    public static final String H2_IN_MEMORY_URL =  "jdbc:h2:mem:";
    public static final List<DnField> BASE_RESERVED_FIELDS = mList(DnField.mkSimple(EPF_LIMIT, DNT_INTEGER),
            DnField.mkSimple(EPF_FROM, DNT_STRING), DnField.mkSimple(EPF_UNTIL, DNT_STRING));

    public DnCxt cxt;
    public String dbName;
    public String dbConfigPrefix;
    public boolean isInMemory;

    public SqlDbBuilder(DnCxt cxt, String dbName, boolean isInMemory) {
        this.cxt = cxt;
        this.dbName = dbName;
        this.dbConfigPrefix = "db." + dbName + ".";
        this.isInMemory = isInMemory;
    }

    public SqlDatabase createDatabase() throws DnException {
        var options = new SqlDbOptions();
        var properties = new Properties();
        String connStr;
        Driver driver;
        int numConnections = 4;
        if (isInMemory) {
            try {
                connStr = H2_IN_MEMORY_URL + cxt.shard;
                driver = DriverManager.getDriver(connStr);
            } catch (SQLException e) {
                throw new DnException(String.format("Cannot get H2 driver for database %s.", dbName),
                        e, DnException.NOT_SUPPORTED, DnException.DATABASE, DnException.CONNECTION);
            }
        } else {
            String dbType = getConfigString(DB_TYPE, POSTGRESQL,
                    String.format("Database type for database %s.", dbName));
            String username;
            String password = "";
            String userDes = String.format("User for accessing database %s.", dbName);
            String passwordKeyDes = String.format("Pointer to private storage of password for database %s.", dbName);
            if (dbType.equals(H2)) {
                username = getConfigString(USER, "sa", userDes);
                String passwordKey = getConfigString(PASSWORD_KEY, null, passwordKeyDes);
                if (passwordKey != null) {
                    var pswd = ConvertUtil.toOptStr(cxt.instanceConfig.get(passwordKey));
                    if (pswd != null) {
                        password = pswd;
                    }
                }
                connStr = "jdbc:h2:~/" + dbName;
            } else {
                if (!dbType.equals(POSTGRESQL)) {
                    throw new DnException("Database type " + dbType + " is not supported.");
                }
                username = getReqConfigString(USER, userDes);
                String passwordKey = getReqConfigString(PASSWORD_KEY, passwordKeyDes);
                password = DnConfigUtil.getReqPrivateStr(cxt, passwordKey);
                String host = getReqConfigString(HOSTNAME, String.format("Hostname for database %s.", dbName));
                if (host.indexOf(':') < 0) {
                    host = host + ":5432";
                }
                String database = getReqConfigString(DATABASE, String.format("Specific database inside " +
                        "database server being targeted for database entry %s.", dbName));

                connStr = "jdbc:" + dbType + "://" + host + "/" + database;
                options.hasSerialType = true;
                options.storesLowerCaseIdentifiersInSchema = true;
                // Debating whether to uncomment next line.
                // options.useTimezoneWithTz = true;

            }
            properties.put("user", username);
            properties.put("password", password);
            numConnections = getConfigInt(NUM_CONNECTIONS, 20, String.format("The number of " +
                    "connections for database %s.", dbName));
            try {
                driver = DriverManager.getDriver(connStr);
            } catch (SQLException e) {
                throw new DnException(String.format("Cannot get driver for database %s using connection string %s.",
                        dbName, connStr),
                        e, DnException.NOT_SUPPORTED, DnException.DATABASE, DnException.CONNECTION);
            }
        }

        // Test the driver.
        try {
            var conn = driver.connect(connStr, properties);
            conn.close();
        } catch (SQLException e) {
            throw new DnException(String.format("Cannot connect to database %s using %s.", dbName, connStr), e,
                    DnException.INTERNAL_ERROR, DnException.DATABASE, DnException.CONNECTION);
        }
        LogSql.log.debug(cxt,
                String.format("Connecting to database %s for shard %s using connection string %s.",
                        dbName, cxt.shard, connStr));
        var reservedFields = getReservedFields();
        var rfMap = nMkMap(reservedFields, (f -> f.name));
        return new SqlDatabase(dbName, driver, connStr, properties, rfMap, options, numConnections);
    }

    public String getReqConfigString(String key, String description) throws DnException {
        String actualKey = dbConfigPrefix + key;
        return DnConfigUtil.getReqConfigString(cxt, actualKey, description);
    }

    public String getConfigString(String key, String dflt, String description) {
        String actualKey = dbConfigPrefix + key;
        return DnConfigUtil.getConfigString(cxt, actualKey, dflt, description);
    }

    public int getConfigInt(String key, int dflt, String description) throws DnException {
        String actualKey = dbConfigPrefix + key;
        return (int)DnConfigUtil.getConfigLong(cxt, actualKey, dflt, description);
    }

    public List<DnField> getReservedFields() {
        // For now, we do not add any ability to inject additional fields.
        return BASE_RESERVED_FIELDS;
    }
}
