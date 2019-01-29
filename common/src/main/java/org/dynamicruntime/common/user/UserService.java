package org.dynamicruntime.common.user;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.context.Priority;
import org.dynamicruntime.context.UserProfile;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnPointer;
import org.dynamicruntime.request.DnServletHandler;
import org.dynamicruntime.sql.DnSqlStatement;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.topic.*;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.user.UserAuthHook;
import org.dynamicruntime.util.EncodeUtil;

import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class UserService implements ServiceInitializer {
    public static final String USER_SERVICE = UserService.class.getSimpleName();
    public SqlTopicService topicService;

    public static UserService get(DnCxt cxt) {
        var obj = cxt.instanceConfig.get(USER_SERVICE);
        return (obj instanceof UserService) ? (UserService)obj : null;
    }

    @Override
    public String getServiceName() {
        return USER_SERVICE;
    }

    @Override
    public void onCreate(DnCxt cxt) throws DnException {
        topicService = SqlTopicService.get(cxt);
        if (topicService == null) {
            throw new DnException("UserService requires SqlTopicService.");
        }
        topicService.registerTopicContainer(SqlTopicConstants.AUTH_TOPIC,
                new SqlTopicInfo(UserTableConstants.UT_TB_AUTH_USERS));
        topicService.registerTopicContainer(SqlTopicConstants.USER_PROFILE_TOPIC,
                new SqlTopicInfo(UserTableConstants.UT_TB_USER_PROFILES));

        // Force table creation of auth topic at startup time.
        var authTopic = topicService.getOrCreateTopic(cxt, SqlTopicConstants.AUTH_TOPIC);
        var sqlCxt = new SqlCxt(cxt, authTopic);
        // Force *primary* shard of all auth tables to be created and populated with *sysadmin* user.
        AuthQueryHolder.get(sqlCxt);

        // Force table creation of userprofile topic at startup.
        topicService.getOrCreateTopic(cxt, SqlTopicConstants.USER_PROFILE_TOPIC);

        // Register hook for doing auth extraction.
        UserAuthHook.extractAuth.registerHookFunction(cxt, "stdExtractAuth", Priority.STANDARD,
                new UserExtractAuthFunction(this));
        // Register hook for doing user profile. This hook assumes a provisional UserProfile has already
        // been created.
        UserAuthHook.loadProfile.registerHookFunction(cxt, "stdLoadProfile", Priority.STANDARD,
                new UserLoadProfileFunction(this));
        // Put in hook to prep auth cookie data.
        UserAuthHook.prepAuthCookies.registerHookFunction(cxt, "stdPrepAuthCookies",
                Priority.EARLY, new UserSetAuthCookiesFunction(this));
    }

    public void addToken(DnCxt cxt, String username, String authId, String authToken, Map<String,Object> rules,
            Date expireDate) throws DnException {
        if (StringUtils.containsWhitespace(authId)) {
            throw DnException.mkConv(String.format("Auth ID %s cannot have whitespace.", authId));
        }
        if (authId.contains("#") || authId.contains(",")) {
            throw DnException.mkConv(String.format("Auth ID %s cannot have a hash ('#') or comma (',').", authId));
        }

        if (expireDate == null) {
            // Sixty days expiration by default.
            expireDate = new Date(cxt.now().getTime() + 60*24*1000*1000L);
        }
        final var ed = expireDate; // So it can be passed into closure.
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);
        aqh.sqlDb.withSession(cxt, () -> {
            AuthUserRow au = aqh.queryByUsername(cxt, username);
            if (au == null) {
                throw new DnException(String.format("User %s does not exist in database.", username),
                        null, DnException.NOT_FOUND, DnException.DATABASE, DnException.IO);
            }
            String hashedToken = EncodeUtil.hashPassword(authToken);
            Map<String,Object> tokenInfo = mMap(AUTH_ID, authId, AUTH_TOKEN, hashedToken,
                    USER_ID, au.userId, AUTH_RULES, rules, EXPIRE_DATE, ed);

            SqlTopicTranProvider.executeTopicTran(sqlCxt, "addToken", null,
                    tokenInfo, () -> {
                var tokenRow = aqh.sqlDb.queryOneDnStatement(cxt, aqh.qAuthToken, tokenInfo);
                DnSqlStatement dnStmt;
                if (tokenRow == null) {
                    // Insert new token.
                    tokenRow = tokenInfo;
                    dnStmt = aqh.iAuthToken;
                } else {
                    tokenRow.putAll(tokenInfo);
                    dnStmt = aqh.uAuthToken;
                }
                SqlTopicUtil.prepForStdExecute(cxt, tokenRow);
                aqh.sqlDb.executeDnStatement(cxt, dnStmt, tokenRow);
            });
        });
    }

    public AuthUserRow queryToken(DnCxt cxt, String authId, String authToken) throws DnException {
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);

        AuthUserRow[] authUser = {null};
        aqh.sqlDb.withSession(cxt, () -> {
            var tokenRow = aqh.sqlDb.queryOneDnStatement(cxt, aqh.qAuthToken, mMap(AUTH_ID, authId));
            if (tokenRow != null) {
                // Validate the authToken.
                Date expireDate = getReqDate(tokenRow, EXPIRE_DATE);
                String hashToken = getReqStr(tokenRow, AUTH_TOKEN);
                if (cxt.now().before(expireDate) && EncodeUtil.checkPassword(authToken, hashToken)) {
                    long userId = getReqLong(tokenRow, USER_ID);
                    AuthUserRow au = aqh.queryByUserId(cxt, userId);
                    if (au != null) {
                        au.authId = authId;
                        au.authRules = getMapDefaultEmpty(tokenRow, AUTH_RULES);
                        authUser[0] = au;
                    }
                }
            }
        });
        return authUser[0];
    }

    public AuthUserRow queryUserId(DnCxt cxt, long userId) throws DnException {
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);
        return aqh.queryByUserId(cxt, userId);
    }

    public void loadProfileRecord(DnCxt cxt) throws DnException {
        UserProfile profile = cxt.userProfile;
        if (profile == null || profile.userId <= DnCxtConstants.AC_SYSTEM_USER_ID) {
            return;
        }
        long userId = profile.userId;
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.USER_PROFILE_TOPIC);
        var profileTopic = sqlCxt.sqlTopic;
        var sqlDb = profileTopic.sqlDb;
        var profileRowPtr = new DnPointer<Map<String,Object>>();
        sqlDb.withSession(cxt, () -> {
            var dbParams = mMap(USER_ID, userId);
            // All the queries we need are at the transaction level of the topic.
            profileRowPtr.value = sqlDb.queryOneDnStatement(cxt, profileTopic.qTranLockQuery, dbParams);
            if (profileRowPtr.value == null) {
                // Need to create a new row.  Note that the transaction will populate the data with userId,
                // userGroup, and a number of other protocol fields.
                Map<String, Object> insertData = mMap(UP_USER_TIMEZONE, profile.timezone.toString(),
                        UP_USER_LOCALE, profile.locale.toString(), UP_USER_DATA, mMap());
                SqlTopicTranProvider.executeTopicTran(sqlCxt, "addUserProfile", null,
                        insertData, () -> {
                            // We did this transaction only to get the record inserted.
                            sqlCxt.tranAlreadyDone = true;
                            profileRowPtr.value = sqlCxt.tranData;
                        });
            }
        });
        var row = profileRowPtr.value;
        profile.timezone = ZoneId.of(getReqStr(Objects.requireNonNull(row), UP_USER_TIMEZONE));
        profile.locale = LocaleUtils.toLocale(getReqStr(row, UP_USER_LOCALE));
        profile.profileData = getMapDefaultEmpty(row, UP_USER_DATA);
        profile.data = row;
    }

    public void authUsingToken(DnCxt cxt, String authId, String tokenData, DnServletHandler servletHandler)
        throws DnException {
        UserAuthData userAuthData = AuthUserUtil.computeUserAuthDataFromToken(cxt, this, authId,
                tokenData, servletHandler);
        cxt.userProfile = userAuthData.createProfile();
        loadProfileRecord(cxt);
    }

    @Override
    public void checkInit(DnCxt cxt) {

    }
}
