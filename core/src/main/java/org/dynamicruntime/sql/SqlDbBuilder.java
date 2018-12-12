package org.dynamicruntime.sql;

import org.dynamicruntime.config.ConfigConstants;
import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.ConvertUtil;

import static org.dynamicruntime.sql.SqlConstants.*;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@SuppressWarnings("WeakerAccess")
public class SqlDbBuilder {
    public static final String H2_IN_MEMORY_URL =  "jdbc:h2:mem:";

    public DnCxt cxt;
    public String dbName;
    public String dbConfigPrefix;

    public SqlDbBuilder(DnCxt cxt, String dbName) {
        this.cxt = cxt;
        this.dbName = dbName;
        this.dbConfigPrefix = "db." + dbName + ".";
    }

    public SqlDatabase createDatabase() throws DnException {
        boolean isInMemory = DnConfigUtil.getConfigBool(cxt, ConfigConstants.IN_MEMORY_SIMULATION, false,
                "Whether code is using H2 in-memory databases to run a simulated version of " +
                        "the application.");
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
        return new SqlDatabase(dbName, driver, connStr, properties, options, numConnections);
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
}
