package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.schemadef.DnTable;
import org.dynamicruntime.sql.DnSqlStatement;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.SqlTableUtil;
import org.dynamicruntime.sql.topic.SqlQueryHolderBase;
import org.dynamicruntime.sql.topic.SqlTopic;
import org.dynamicruntime.sql.topic.SqlTopicConstants;
import org.dynamicruntime.sql.topic.SqlTopicUtil;

import java.util.Map;

import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.user.UserConstants.*;

/**
 * The base tables for authenticated user functionality. If you contrast this implementation
 * with Hibernate style solutions, you will notice two things.
 *
 * * There is a lot more code just to be able to execute some simple queries.
 * * The design of the tables in this solution is not dictated by the design of Java classes.
 * * The code has little reference to actual fields in the tables and places little expectations
 * on the design of the tables.
 *
 * This code took multiple days to write while an equivalent hibernate solution might have taken hours. But
 * those days are paid only once and pay dividends for years. The profits are as follows.
 *
 * * The code can be fine tuned by simply tweaking the code. If you want to do something conditionally against one
 * variable based on the contents of another, it is much easier to add it here.
 * * The code can run successfully against two (slightly) different designed versions without change, especially
 * if you add conditional code to do the necessary tweaks to be compatible with the different versions
 * simultaneously. This is the primary justification for writing the code this way.
 * * The code can be easily modified to add temporary debug console output focused on a very particular
 * event.
 * * It is easier to add application specific metrics to the code. Auditing authentication activity is
 * a popular thing to do and having complete control over how it's done for large mature applications
 * can be a winner.
 * * When there are errors, the errors are reported more explicitly. For example, if the database is
 * having problems with a particular prepared statement, it can be easier to figure it out.
 */
@SuppressWarnings("WeakerAccess")
public class AuthQueryHolder extends SqlQueryHolderBase {
    public static final String AUTH_QUERY_HOLDER = AuthQueryHolder.class.getSimpleName();

    /** AuthUsers, additional queries. */
    public DnSqlStatement qUsername;
    public DnSqlStatement qPrimaryId;

    /** Contacts */
    public DnTable contacts;
    public DnSqlStatement iContact;
    public DnSqlStatement qContact;
    public DnSqlStatement qContactsByUser;
    public DnSqlStatement qContactByContactId;
    public DnSqlStatement uContact;
    /** AuthTokens */
    public DnTable authTokens;
    public DnSqlStatement iAuthToken;
    public DnSqlStatement uAuthToken;
    public DnSqlStatement qAuthToken;
    /** Login sources. */
    public DnTable sources;
    public DnSqlStatement iSource;
    public DnSqlStatement qSource;
    public DnSqlStatement uSource;

    public AuthQueryHolder(String name, SqlTopic sqlTopic) {
        super(name, sqlTopic);
    }

    public static AuthQueryHolder get(SqlCxt sqlCxt) throws DnException {
        if (!sqlCxt.topic.equals(SqlTopicConstants.AUTH_TOPIC)) {
            throw new DnException("Cannot retrieve auth queries from topic " + sqlCxt.topic + ".");
        }
        return sqlCxt.getOrCreateQueryHolder(AUTH_QUERY_HOLDER,
                () -> new AuthQueryHolder(AUTH_QUERY_HOLDER, sqlCxt.sqlTopic));
    }

    @Override
    public void init(SqlCxt sqlCxt) throws DnException {
        boolean firstTime = initContacts(sqlCxt);
        initAuthTokens(sqlCxt);
        initSources(sqlCxt);
        initExtraAuth(sqlCxt);
        if (firstTime) {
            addInitialRows(sqlCxt);
        }
    }


    public boolean initContacts(SqlCxt sqlCxt) throws DnException {
        DnCxt cxt = sqlCxt.cxt;

        contacts = cxt.getSchema().getTableMustExist(UserTableConstants.UT_TB_AUTH_CONTACTS);
        boolean firstTime = SqlTableUtil.checkCreateTable(sqlCxt, contacts);

        iContact = SqlTopicUtil.mkTableInsertStmt(sqlCxt, contacts);
        qContact = SqlTopicUtil.mkTableSelectStmt(sqlCxt, contacts);
        qContactsByUser = SqlTopicUtil.mkNamedTableSelectStmt(sqlCxt, "qByUser" + contacts.tableName,
                contacts, mList(USER_ID, VERIFIED, ENABLED));
        qContactByContactId = SqlTopicUtil.mkNamedTableSelectStmt(sqlCxt, "qByContactId" + contacts.tableName,
                contacts, mList(CT_CONTACT_ID));
        // Note that this is *not* an update based on contactId but on the primary key of userId, contactType,
        // and contactAddress.
        uContact = SqlTopicUtil.mkTableUpdateStmt(sqlCxt, contacts);

        return firstTime;
    }

    public void initAuthTokens(SqlCxt sqlCxt) throws DnException {
        DnCxt cxt = sqlCxt.cxt;

        authTokens = cxt.getSchema().getTable(UserTableConstants.UT_TB_AUTH_TOKENS);
        SqlTableUtil.checkCreateTable(sqlCxt, authTokens);

        iAuthToken = SqlTopicUtil.mkTableInsertStmt(sqlCxt, authTokens);
        uAuthToken = SqlTopicUtil.mkTableUpdateStmt(sqlCxt, authTokens);
        qAuthToken = SqlTopicUtil.mkTableSelectStmt(sqlCxt, authTokens);
    }

    public void initSources(SqlCxt sqlCxt) throws DnException {
        DnCxt cxt = sqlCxt.cxt;


        sources = cxt.getSchema().getTableMustExist(UserTableConstants.UT_TB_AUTH_LOGIN_SOURCES);
        SqlTableUtil.checkCreateTable(sqlCxt, sources);

        iSource = SqlTopicUtil.mkTableInsertStmt(sqlCxt, sources);
        qSource = SqlTopicUtil.mkTableSelectStmt(sqlCxt, sources);
        uSource = SqlTopicUtil.mkTableUpdateStmt(sqlCxt, sources);
    }

    public void initExtraAuth(SqlCxt sqlCxt) {
        var authTable = sqlTopic.table;
        qUsername = SqlTopicUtil.mkNamedTableSelectStmt(sqlCxt, "qUsername" + authTable.tableName,
                authTable, mList(AUTH_USERNAME));
        qPrimaryId = SqlTopicUtil.mkNamedTableSelectStmt(sqlCxt, "qPrimaryId" + authTable.tableName,
                authTable, mList(AUTH_USER_PRIMARY_ID));
    }

    public void addInitialRows(SqlCxt sqlCxt) throws DnException {
        // Add initial sysadmin user if it is missing.
        var cxt = sqlCxt.cxt;
        String sysadminEmail = DnConfigUtil.getConfigString(cxt, "user.sysadmin.email",
                "sysadmin@mg.dynamicruntime.org",
                "Password given to *sysadmin* user at initial provisioning");
        Map<String,Object> sysUser = AuthUserRow.mkInitialUser(sysadminEmail, DnCxtConstants.AC_LOCAL,
                DnCxtConstants.AC_LOCAL, ROLE_ADMIN);

        var byUsernameRow = sqlDb.queryOneDnStatement(cxt, qUsername, mMap(AUTH_USERNAME, "sysadmin"));
        if (byUsernameRow != null) {
            // Email has changed, update it.
            String curEmail = getReqStr(byUsernameRow, AUTH_USER_PRIMARY_ID);
            if (!curEmail.equals(sysadminEmail)) {
                byUsernameRow.put(LAST_TRAN_ID, "emailTo:" + sysadminEmail);

                byUsernameRow.put(AUTH_USER_PRIMARY_ID, sysadminEmail);
                SqlTopicUtil.prepForStdExecute(cxt, byUsernameRow);
                sqlDb.executeDnStatement(cxt, sqlTopic.uTranLockQuery, byUsernameRow);
            }
        } else {
            // Use the initial data to do a query for the user.
            var existingRow = sqlDb.queryOneDnStatement(cxt, qPrimaryId, sysUser);

            if (existingRow == null) {
                sysUser.put(AUTH_USERNAME, "sysadmin");
                insertAuthUser(cxt, sysUser);
            }
        }
    }

    //
    // Convenience methods for executing queries. These methods assume you are executing inside
    // an SQL session.
    //

    public void insertAuthUser(DnCxt cxt, Map<String,Object> userDefData) throws DnException {
        SqlTopicUtil.prepForTranInsert(cxt, userDefData);
        SqlTopicUtil.prepForStdExecute(cxt, userDefData);
        sqlDb.executeDnStatement(cxt, sqlTopic.iTranLockQuery, userDefData);
    }

    public AuthUserRow queryByUserId(DnCxt cxt, long userId) throws DnException {
        var params = mMap(USER_ID, userId);
        var row = sqlDb.queryOneDnStatement(cxt, sqlTopic.qTranLockQuery, params);
        return (row != null) ? AuthUserRow.extract(row) : null;
    }

    @SuppressWarnings("Duplicates")
    public AuthUserRow queryByPrimaryId(DnCxt cxt, String primaryId) throws DnException {
        var params = mMap(AUTH_USER_PRIMARY_ID, primaryId);
        var row = sqlDb.queryOneDnStatement(cxt, qPrimaryId, params);
        return (row != null) ? AuthUserRow.extract(row) : null;

    }

    @SuppressWarnings("Duplicates")
    public AuthUserRow queryByUsername(DnCxt cxt, String username) throws DnException {
        var params = mMap(AUTH_USERNAME, username);
        var row = sqlDb.queryOneDnStatement(cxt, qUsername, params);
        return (row != null) ? AuthUserRow.extract(row) : null;
    }
}
