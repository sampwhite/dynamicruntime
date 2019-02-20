package org.dynamicruntime.common.user;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.context.Priority;
import org.dynamicruntime.context.UserProfile;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnFunction;
import org.dynamicruntime.function.DnPointer;
import org.dynamicruntime.request.DnServletHandler;
import org.dynamicruntime.sql.DnSqlStatement;
import org.dynamicruntime.sql.SqlCxt;
import org.dynamicruntime.sql.topic.*;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.user.LogUser;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.user.UserAuthHook;
import org.dynamicruntime.user.UserSourceId;
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
    /** Short timeout to allow testing. This is catering to scenarios when there is more than one request per second
     * for a user in short bursts. */
    public static final int PROFILE_CACHE_TIMEOUT_IN_SECS = 10;
    public static final String USER_SERVICE = UserService.class.getSimpleName();
    public SqlTopicService topicService;
    public final AuthFormHandler formHandler = new AuthFormHandler(this);
    public final UserCache userCache = new UserCache();

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
        formHandler.init(cxt);

        // Register the topics this service interfaces with.
        topicService.registerTopicContainer(SqlTopicConstants.AUTH_TOPIC,
                new SqlTopicInfo(UserTableConstants.UT_TB_AUTH_USERS));
        topicService.registerTopicContainer(SqlTopicConstants.USER_PROFILE_TOPIC,
                new SqlTopicInfo(UserTableConstants.UT_TB_USER_PROFILES));

        // Force table creation of auth topic at startup time.
        var authTopic = topicService.getOrCreateTopic(cxt, SqlTopicConstants.AUTH_TOPIC);
        var sqlCxt = new SqlCxt(cxt, authTopic);
        // Force *primary* shard of all auth tables to be created and populated with *sysadmin* user.
        AuthQueryHolder.get(sqlCxt);

        // Force table creation of userprofile topic at startup. Currently there is only one
        // table in the profile and it is the table that also does transactions.
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

    public void addAdminToken(DnCxt cxt, String username, String authId, String authToken, Map<String,Object> rules,
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
            if (!au.enabled) {
                throw new DnException(String.format("User %s is no longer active.", username), null,
                        DnException.NOT_FOUND, DnException.SYSTEM, DnException.CODE);
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

    /** Does a simple admin token login. Does not need all the extras of a regular user login. In particular,
     * it does not have to deal with login sources and in fact it does not get tracked for login source.
     * Contrast this method with {@link AuthFormHandler#doLogin}.*/
    public void authUsingAdminToken(DnCxt cxt, String authId, String tokenData, DnServletHandler servletHandler)
            throws DnException {
        UserAuthData userAuthData = AuthUserUtil.computeUserAuthDataFromToken(cxt, this, authId,
                tokenData, servletHandler);
        cxt.userProfile = userAuthData.createProfile();
        loadProfileRecord(cxt, cxt.userProfile, false);
    }

    public AuthUserRow queryByAdminCacheToken(DnCxt cxt, String authId, String authToken) throws DnException {
        String tokenKey = authId + ":" + authToken;
        return userCache.getAuthDataByToken(tokenKey, PROFILE_CACHE_TIMEOUT_IN_SECS,
                datedItem -> queryByAdminToken(cxt, authId, authToken));
    }

    public AuthUserRow queryByAdminToken(DnCxt cxt, String authId, String authToken) throws DnException {
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
                    if (au != null && au.enabled) {
                        au.authId = authId;
                        au.authRules = getMapDefaultEmpty(tokenRow, AUTH_RULES);
                        authUser[0] = au;
                    }
                }
            }
        });
        return authUser[0];
    }

    public AuthUserRow queryCacheUserId(DnCxt cxt, long userId, int timeoutSeconds) throws DnException {
        return userCache.getAuthUserRow(userId, timeoutSeconds, datedItem -> queryUserId(cxt, userId));
    }


    public AuthUserRow queryUser(DnCxt cxt, DnFunction<AuthQueryHolder,AuthUserRow> function)
            throws DnException {
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);
        var userRowPtr = new DnPointer<AuthUserRow>();
        aqh.sqlDb.withSession(cxt, () -> userRowPtr.value = function.apply(aqh));
        return userRowPtr.value;
    }

    public AuthUserRow queryUserId(DnCxt cxt, long userId) throws DnException {
        return queryUser(cxt, aqh -> aqh.queryByUserId(cxt, userId));
    }

    public AuthUserRow queryUsername(DnCxt cxt, String username) throws DnException {
        return queryUser(cxt, aqh -> aqh.queryByUsername(cxt, username));
    }

    public AuthUserRow queryPrimaryId(DnCxt cxt, String primaryId) throws DnException {
        return queryUser(cxt, aqh -> aqh.queryByPrimaryId(cxt, primaryId));
    }
    public void loadProfileRecord(DnCxt cxt, UserProfile profile, boolean forceRefresh) throws DnException {
        if (profile == null) {
            return;
        }
        AuthAllUserData allData = new AuthAllUserData(profile.userId, null, null);
        allData.profile = profile;
        loadProfileRecord(cxt, "loadProfile", allData, forceRefresh);
    }

    public void loadProfileRecord(DnCxt cxt, String tranName, AuthAllUserData allData, boolean forceRefresh)
            throws DnException {
        UserProfile profile = allData.profile;
        if (profile == null || profile.userId <= DnCxtConstants.AC_SYSTEM_USER_ID) {
            return;
        }
        // The profile.modifiedDate will come from cookie authentication.
        var doForceRefresh = forceRefresh;
        long userId = profile.userId;
        Date cookieDate = profile.cookieModifiedDate;
        int timeout = doForceRefresh ? -1 : PROFILE_CACHE_TIMEOUT_IN_SECS;
        if (cookieDate != null) {
            int secondsDiff = (int)((cxt.now().getTime() - cookieDate.getTime())/1000);
            if (secondsDiff < timeout) {
                timeout = secondsDiff;
            }
        }
        var row = userCache.getProfileData(userId, timeout, datedItem ->
                getOrCreateProfileRow(cxt, tranName, allData));
        Date modifiedDate = getReqDate(row, MODIFIED_DATE);
        // Other node may have updated cookie with information that the profile has changed.
        if (!doForceRefresh && profile.modifiedDate != null && profile.modifiedDate.after(modifiedDate)) {
            doForceRefresh = true;
            timeout = -1;
            // Get the row again.
            row = userCache.getProfileData(userId, timeout, datedItem ->
                    getOrCreateProfileRow(cxt, tranName + "Forced", allData));
            modifiedDate = getReqDate(row, MODIFIED_DATE);
        }
        profile.timezone = ZoneId.of(getReqStr(Objects.requireNonNull(row), UP_USER_TIMEZONE));
        profile.locale = LocaleUtils.toLocale(getReqStr(row, UP_USER_LOCALE));
        profile.profileData = getMapDefaultEmpty(row, UP_USER_DATA);
        profile.modifiedDate = modifiedDate;
        profile.didForceRefresh = doForceRefresh;
        profile.data = row;
    }

    public Map<String,Object> getOrCreateProfileRow(DnCxt cxt, String tranName, AuthAllUserData allData)
            throws DnException {
        UserProfile profile = allData.profile;
        long userId = allData.userId;
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.USER_PROFILE_TOPIC);
        var profileTopic = Objects.requireNonNull(sqlCxt.sqlTopic);
        var sqlDb = profileTopic.sqlDb;
        var profileRowPtr = new DnPointer<Map<String,Object>>();
        sqlDb.withSession(cxt, () -> {
            var dbParams = mMap(USER_ID, userId);
            // All the queries we need are at the transaction level of the topic.
            var row = sqlDb.queryOneDnStatement(cxt, profileTopic.qTranLockQuery, dbParams);
            boolean needsUpdate = false;
            Map<String,Object> profileUserData;
            if (row != null) {
                profileUserData = getMapDefaultEmpty(row, UP_USER_DATA);
                String username = getOptStr(profileUserData, UP_PUBLIC_NAME);
                if (profile.publicName != null && (username == null || !username.equals(profile.publicName))) {
                    needsUpdate = true;
                    profileUserData.put(UP_PUBLIC_NAME, profile.publicName);
                }
            } else {
                profileUserData = mMap(UP_PUBLIC_NAME, profile.publicName);
            }
            // Copy over initial contacts from auth provisioning (if necessary).
            if (!profileUserData.containsKey(CTE_CONTACTS)) {
                var authContacts = getOptListOfMaps(profile.authData, CTE_CONTACTS);
                if (authContacts != null && authContacts.size() > 0) {
                    profileUserData.put(CTE_CONTACTS, authContacts);
                    needsUpdate = (row != null);
                }
            }

            // See if we need to capture some changes in the login sourceId state.
            if (allData.updateProfileSourceId) {
                Map<String,Object> sourceIdData = getMapDefaultEmpty(profileUserData, UP_LOGIN_SOURCES);
                UserSourceId sourceId = new UserSourceId(false, "canBeIgnored",
                        null);
                try {
                    sourceId.fillFromSourceData(sourceIdData);
                } catch (DnException e) {
                    LogUser.log.error(cxt, e, "Failed to fill in login source data from profile.");
                }
                sourceId.addSource(cxt, allData.ipAddress, allData.userAgent);
                profileUserData.put(UP_LOGIN_SOURCES, sourceId.toProfileMap());
                needsUpdate = (row != null);
            }

            // Capture into another variable so it can be used in code blocks below.
            final boolean doUpdate = needsUpdate;

            if (row == null || doUpdate) {
                // Need to create a new row.  Note that the transaction will populate the data with userId,
                // userGroup, and a number of other protocol fields.
                Map<String, Object> tranData = mMap(USER_ID, userId, USER_GROUP, profile.userGroup,
                        UP_USER_TIMEZONE, profile.timezone.toString(),
                        UP_USER_LOCALE, profile.locale.toString(), UP_USER_DATA, profileUserData);
                SqlTopicTranProvider.executeTopicTran(sqlCxt, tranName, null,
                        tranData, () -> {
                            if (doUpdate) {
                                // Update profile data with data extracted from auth data. This is
                                // meant as a motivating example, not a real useful usage.
                                sqlCxt.tranData.put(UP_USER_DATA, profileUserData);
                            } else {
                                // We did this transaction only to get the record inserted, skip the update.
                                sqlCxt.tranAlreadyDone = true;
                            }
                            profileRowPtr.value = sqlCxt.tranData;
                        });
            } else {
                profileRowPtr.value = row;
            }
        });
        return profileRowPtr.value;
    }


    @Override
    public void checkInit(DnCxt cxt) {

    }
}
