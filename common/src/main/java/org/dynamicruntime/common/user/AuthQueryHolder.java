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
import org.dynamicruntime.user.UserConstants;

import java.util.Map;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

@SuppressWarnings("WeakerAccess")
public class AuthQueryHolder extends SqlQueryHolderBase {
    public static String AUTH_QUERY_HOLDER = AuthQueryHolder.class.getSimpleName();

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
                contacts, mList(UserConstants.CT_CONTACT_ID));
        // Note that this is *not* an update based on contactId but on the primary key of userId, contactType,
        // and contactAddress.
        uContact = SqlTopicUtil.mkTableUpdateStmt(sqlCxt, contacts);

        return firstTime;
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
                authTable, mList(UserConstants.AUTH_USERNAME));
        qPrimaryId = SqlTopicUtil.mkNamedTableSelectStmt(sqlCxt, "qPrimaryId" + authTable.tableName,
                authTable, mList(UserConstants.AUTH_USER_PRIMARY_ID));
    }

    public void addInitialRows(SqlCxt sqlCxt) throws DnException {
        // Add initial sysadmin user if it is missing.
        var cxt = sqlCxt.cxt;
        String sysadminEmail = DnConfigUtil.getConfigString(cxt, "user.sysadmin.email",
                "sysadmin@dynamicruntime.org",
                "Password given to *sysadmin* user at initial provisioning");
        Map<String,Object> sysUser = AuthUser.mkInitialUser(sysadminEmail, DnCxtConstants.AC_LOCAL,
                DnCxtConstants.AC_LOCAL, UserConstants.ROLE_ADMIN);

        // Use the initial data to do a query for the user.
        var existingRow = sqlDb.queryOneDnStatement(cxt, qPrimaryId, sysUser);
        // For now, only insert, not update (later may add update logic as schema evolves).
        if (existingRow == null) {
            sysUser.put(UserConstants.AUTH_USERNAME, "sysadmin");
            SqlTopicUtil.prepForTranInsert(cxt, sysUser);
            SqlTopicUtil.prepForStdExecute(cxt, sysUser);
            sqlDb.executeDnStatement(cxt, sqlTopic.iTranLockQuery, sysUser);
        }

    }
}
