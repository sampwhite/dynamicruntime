package org.dynamicruntime.common.user;

import org.dynamicruntime.common.mail.DnMailService;
import org.dynamicruntime.content.DnContentService;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnPointer;
import org.dynamicruntime.node.DnCoreNodeService;
import org.dynamicruntime.sql.topic.SqlTopicConstants;
import org.dynamicruntime.sql.topic.SqlTopicService;
import org.dynamicruntime.sql.topic.SqlTopicTranProvider;
import org.dynamicruntime.sql.topic.SqlTopicUtil;
import org.dynamicruntime.user.LogUser;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.user.UserContact;
import org.dynamicruntime.user.UserSourceId;
import org.dynamicruntime.util.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

/**
 * Code that handles the registration and login forms.
 */
@SuppressWarnings("WeakerAccess")
public class AuthFormHandler {
    public final int TOKEN_TIMEOUT_MILLIS = 15 * 60 * 1000; // Fifteen minutes
    public final UserService userService;
    public DnCoreNodeService coreNodeService;
    public DnMailService mailService;
    public DnContentService contentService;

    // Used to prevent abuse of our free website.
    public int maxExecsPerHour = 100;
    public int curNumberOfExecs = 0;
    public int curHourScope = 0;
    public AtomicInteger activeRequests = new AtomicInteger(0);

    public AuthFormHandler(UserService userService) {
        this.userService = userService;
    }

    public void init(DnCxt cxt) {
        coreNodeService = DnCoreNodeService.get(cxt);
        contentService = DnContentService.get(cxt);
        mailService = DnMailService.get(cxt);
    }

    public Map<String,Object> generateFormTokenData(DnCxt cxt) throws DnException {
        String dateStr = DnDateUtil.formatDate(cxt.now());
        String formAuthType = FMT_SIMPLE;
        // Simulate captcha number of possible legal answers. In the future, the value
        // computed here may be very different.
        String formCode = "" + RandomUtil.getIntegerInRange(1, 1000);

        // Put in some random bytes at the end for hashing with communication targets.
        // We do not need a lot because we are not creating a solution that can survive more
        // than a billion brute force checks. The only place that can do the check
        // is in code that has access to the encryption key, so we can make sure to not allow anything
        // even close to that many checks.
        String randomStr = EncodeUtil.mkRndString(4);
        String tokenValue = formAuthType + "@" + dateStr + formCode + ":" + randomStr;
        // Encrypting also provides randomness for free.
        String formToken = Objects.requireNonNull(coreNodeService).encryptString(tokenValue);

        return mMap(FM_FORM_AUTH_TYPE, formAuthType, FM_FORM_AUTH_TOKEN, formToken,
                FM_CAPTCHA_DATA, mMap(FM_FORM_AUTH_CODE, formCode));
    }

    public void sendVerifyToken(DnCxt cxt, UserContact contact, String formAuthToken,
            String formAuthCode) throws DnException {
        String contactType = contact.cType;
        String contactAddress = contact.address;
        if (!"email".equals(contactType)) {
            throw DnException.mkInput("Only a contact type of *email* is currently supported.");
        }
        if (!contactAddress.contains("@")) {
            throw DnException.mkInput("Email address requires an at ('@') symbol.");
        }
        if (!validateTokenCode(cxt, formAuthToken, formAuthCode)) {
            throw DnException.mkInput("THe validation code is either incorrect or has expired.");
        }

        String verifyCode = computeVerifyCode(cxt, formAuthToken, contactAddress);
        var contentData = contentService.getTemplateContent(cxt, "auth/contactCodeTextEmail.ftl",
                null, mMap("verifyCode", verifyCode));
        String text = Objects.requireNonNull(contentData).strContent;
        var mailData = mailService.createMailData(contactAddress, mailService.fromAddressForApp,
                "Your verification code", text, null);
        mailService.sendEmail(cxt, mailData);
    }

    public AuthUserRow createInitialUser(DnCxt cxt, UserContact contact, String formAuthToken, String verifyCode)
        throws DnException {
        String contactAddress = contact.address;
        if (!validateVerifyCode(cxt, formAuthToken, contactAddress, verifyCode)) {
            throw DnException.mkInput("Verification code is incorrect.");
        }

        contact.usage = CTU_REGISTRATION;

        // Must specify account and group. Other mechanisms for creating users will set different values here.
        String account = DnCxtConstants.AC_PUBLIC;

        // When sharding becomes a critical part of the app, sharding rules should be put here and the
        // shard value should be set in the *cxt*.
        // In particular, we may have a shard called *public* separate from *primary*. But for now
        // we ignore all such complications.

        // Find or create user. This code should go into its own method when it gets sufficiently complex.
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);
        var rowPtr = new DnPointer<AuthUserRow>();
        aqh.sqlDb.withSession(cxt, () -> {
            // First query.
            var curRow = aqh.queryByPrimaryId(cxt, contactAddress);
            Map<String,Object> userData;

            Map<String,Object> data = curRow != null ? curRow.data : mMap();

            // Check to see if we have an active user. We cannot create an initial user on top of
            // a currently active user. Also, we cannot do this on top of a a user without the USER role.
            if (curRow != null && curRow.enabled) {
                if ((curRow.username != null && !curRow.username.startsWith(AUTH_USERNAME_TMP_PREFIX)) ||
                    curRow.encodedPassword != null) {
                    throw new DnException(String.format("Email %s is not available for creating a new user.",
                            contactAddress), DnException.NOT_AUTHORIZED);
                }
                if (!curRow.roles.contains(ROLE_USER)) {
                    throw new DnException("The specified user does not support a contact verified login " +
                            "creation process.");
                }
            }

            // This will clear any left over data in an existing row as well as provision a new row.
            data.putAll(AuthUserRow.mkInitialUser(contactAddress, account, account /* group */, ROLE_USER));

            // AUTH_USER_DATA should always exist.
            userData = getMapDefaultEmpty(data, AUTH_USER_DATA);
            userData.put(CTE_VALIDATED_CONTACTS, mList(contactAddress));
            userData.put(CTE_CONTACTS, mList(contact.toMap()));
            if (curRow != null) {
                if (!aqh.updateAuthUser(cxt, data)) {
                    throw new DnException("Unexpected failure to update user row.");
                }
                rowPtr.value = curRow;
            } else {
                long userId = aqh.insertAuthUser(cxt, data);
                data.put(USER_ID, userId);
                rowPtr.value = AuthUserRow.extract(data);
            }
        });

        return rowPtr.value;
    }

    /** Used to set the username and password for a user. The data that is populated into AuthAllUserData can be
     * used by the caller to create the authentication & source cookies. */
    public void setLoginDataAndCreateProfile(DnCxt cxt, AuthAllUserData allData, String username, String password,
            String formAuthToken, String verifyCode) throws DnException {
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);
        long userId = allData.userId;
        var userIdParam = mMap(USER_ID, userId);

        aqh.sqlDb.withSession(cxt, () -> {
            // Look for potential update conflict early so we can give a nice error to it.
            AuthUserRow usernameRow = aqh.queryByUsername(cxt, username);
            if (usernameRow != null && usernameRow.userId != userId) {
                throw new DnException(String.format("Username %s has already been taken.",
                        username), DnException.CONFLICT);
            }
            SqlTopicTranProvider.executeTopicTran(sqlCxt, "setLoginData", null,
                    userIdParam, () -> {
                        allData.authRow = AuthUserRow.extract(sqlCxt.tranData);
                        // Verify code.
                        validateVerifyCode(cxt, formAuthToken, allData.authRow.primaryId, verifyCode);
                        // Populate or update sourceId. This inserts/updates rows in the login sources table.
                        updateSourceId(cxt, allData, aqh);
                        // Do the actual work of this method.
                        setLoginDataTranInternal(allData, username, password);
                         // Carry results back into database row.
                        sqlCxt.tranData.putAll(allData.authRow.toMap());
                    });
        });
        if (allData.authData == null || allData.profile == null) {
            // This should never happen.
            throw new DnException("Unable to perform user auth action.");
        }

        // Create initial profile record, or if it exists, sync up data from auth row.
        allData.updateProfileSourceId = true;
        userService.loadProfileRecord(cxt, allData, true);
    }

    /** Updates the sourceId information in the login sources table.
     * Should only be called in a database transaction and with *authRow* in AuthAllUserData already loaded. */
    public void updateSourceId(DnCxt cxt, AuthAllUserData allData, AuthQueryHolder aqh) throws DnException {
        UserSourceId sourceId = allData.sourceId;
        UserSourceId tSourceId = null; // Source ID from or for login sources tables.
        boolean doInsert = false;
        if (sourceId != null) {
            // Query for it.
            tSourceId = aqh.queryLoginSourceId(cxt, allData.userId, sourceId.sourceCode);
        }
        if (tSourceId == null) {
            String ipAddress = cxt.forwardedFor != null ? cxt.forwardedFor : "127.0.0.1";
            List<UserSourceId> recentIds = aqh.queryRecentLoginSourceIds(cxt, allData.userId);
            tSourceId = findItem(recentIds, sId ->
                    findItem(sId.ipAddresses, ipA -> ipA.ipAddress.equals(ipAddress)) != null);
            if (tSourceId == null) {
                doInsert = true;
                tSourceId = UserSourceId.createNew();
                tSourceId.userId = allData.userId;
            }
        }
        tSourceId.addSource(cxt, allData.ipAddress, allData.userAgent);
        if (doInsert || tSourceId.isModified) {
            // Make sure we have all the related user data available for doing an update, and if user
            // fields have changed, make sure the change is picked up as well.
            tSourceId.data = SqlTopicUtil.mergeUserFields(tSourceId.data, allData.authRow.data);
            aqh.updateLoginSourceId(cxt, tSourceId, doInsert);
        }
        // Since we went through the work of interacting with the database, we might as well refresh
        // the cookie in the browser as well.
        tSourceId.forceRegenerateCookie = true;
        allData.sourceId = tSourceId;
    }

    public void setLoginDataTranInternal(AuthAllUserData allData, String username, String password)
            throws DnException {
        AuthUserRow userRow = allData.authRow;
        checkValidUsername(username);

        // Verify row supports setting a username and password.
        if (!userRow.enabled || !userRow.roles.contains(ROLE_USER) ||
            !userRow.authUserData.containsKey(CTE_CONTACTS)) {
            throw new DnException("User is not in an appropriate state to get a login assigned to it.");
        }

        userRow.username = username;
        userRow.passwordEncodingRule = AUTH_DN_HASH;
        userRow.encodedPassword = EncodeUtil.hashPassword(password);
        UserAuthData authData = new UserAuthData();
        userRow.populateAuthData(authData);
        if (allData.sourceId != null) {
            authData.sourceId = allData.sourceId.sourceCode;
        }
        authData.determinedUserId = true;
        allData.authData = authData;
        allData.profile = authData.createProfile();
    }

    public static void checkValidUsername(String username) throws DnException {
        // When this code gets internationalized, we will need to allow other non-separator characters
        // into the username.
        if (!StrUtil.isJavaName(username) || username.length() < 4) {
            throw DnException.mkInput(String.format("Username '%s' has invalid characters or matches " +
                    "a disallowed reserved word. A username should start with a letter, have only " +
                    "letters, numbers, or underscores. It should also be at least four characters in length.",
                    username));
        }
    }

    public boolean validateTokenCode(DnCxt cxt, String formAuthToken, String formAuthCode) throws DnException {
        checkForMaximumRequests(cxt);
        String suffix = getTokenSuffix(cxt, formAuthToken);
        String code = StrUtil.getToNextIndex(suffix, 0, ":");
        return code.equals(formAuthCode);
    }

    public boolean validateVerifyCode(DnCxt cxt, String formAuthToken, String contactAddress, String verifyCode)
            throws DnException {
        checkForMaximumRequests(cxt);
        // Do the getTokenSuffix, just for the token validation.
        String code = computeVerifyCode(cxt, formAuthToken, contactAddress);

        return code.equals(verifyCode);
    }

    public String computeVerifyCode(DnCxt cxt, String formAuthToken, String contactAddress) throws DnException {
        byte[] hash = EncodeUtil.stdHashToBytes(contactAddress + formAuthToken);
        getTokenSuffix(cxt, formAuthToken);
        return EncodeUtil.convertToReadableChars(hash, 4);
    }

    public String getTokenSuffix(DnCxt cxt, String formAuthToken) throws DnException {
        String tokenValue = Objects.requireNonNull(coreNodeService.decryptString(formAuthToken));
        int index = tokenValue.indexOf('@');
        if (index <= 0) {
            throw new DnException("Form auth token is not valid.");
        }
        String tokenType = tokenValue.substring(0, index);
        if (!tokenType.equals(FMT_SIMPLE)) {
            throw DnException.mkConv("Form auth token is not a valid token type.");
        }
        int index2 = tokenValue.indexOf('Z', index + 1);
        String dateStr = tokenValue.substring(index + 1, index2);
        Date d = DnDateUtil.parseDate(dateStr);
        Date now = cxt.now();
        if (now.getTime() - d.getTime() > TOKEN_TIMEOUT_MILLIS) {
            throw DnException.mkInput("Form auth token has timed out.");
        }
        return tokenValue.substring(index2 + 1);
    }

    public void checkForMaximumRequests(DnCxt cxt) throws DnException {
        // In a real prod environment, we would look at the forwardedFor address and count on
        // a per forwardedFor address basis.

        // Prevent pile up.
        int numRequests = activeRequests.incrementAndGet();
        if (numRequests > 3) {
            throw new DnException("There are more than three active authentication requests occurring " +
                    "simultaneously on this node.");
        }
        try {
            synchronized (this) {
                int curHour = (int)(System.currentTimeMillis() / (3600 * 1000L));
                if (curHour != curHourScope) {
                    curHourScope = curHour;
                    curNumberOfExecs = 0;
                } else {
                    if (curNumberOfExecs++ > maxExecsPerHour) {
                        throw new DnException("Too many authentication related requests have occurred in the last hour.");
                    }
                    if (curNumberOfExecs > maxExecsPerHour/3) {
                        // Slow things down.
                        LogUser.log.info(cxt, "Too many auth related requests coming during this hour.");
                        SystemUtil.sleep(10000);
                    }
                }
            }
        } finally {
            activeRequests.decrementAndGet();
        }
    }
}
