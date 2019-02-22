package org.dynamicruntime.common.user;

import org.dynamicruntime.common.mail.DnMailService;
import org.dynamicruntime.content.DnContentService;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.DnCxtConstants;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.function.DnPointer;
import org.dynamicruntime.node.DnCoreNodeService;
import org.dynamicruntime.schemadata.CoreConstants;
import org.dynamicruntime.sql.topic.SqlTopicConstants;
import org.dynamicruntime.sql.topic.SqlTopicService;
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
 *
 * Like all authentication code that I have every seen, it is complicated because it
 * is trying to do complicated and subtle things.
 */
@SuppressWarnings("WeakerAccess")
public class AuthFormHandler {
    public static class FormTokenComponents {
        public final String tType;
        public final String suffix;
        public final Date creationDate;
        public final String extraData;

        public FormTokenComponents(String tType, String suffix, Date creationDate, String extraData) {
            this.tType = tType;
            this.suffix = suffix;
            this.creationDate = creationDate;
            this.extraData = extraData;
        }
    }
    /** Used to set username and password, not to validate them. Value for username and password can be null. */
    public static class VerifyLoginParams {
        public final String formAuthToken;
        public final String verifyCode;
        public final String username;
        public final String password;
        /** Whether this is a password check. If this is false then we are using the *verifyCode*. */
        public boolean checkPassword;

        public VerifyLoginParams(String formAuthToken, String verifyCode, String username, String password) {
            this.formAuthToken = formAuthToken;
            this.verifyCode = verifyCode;
            this.username = username;
            this.password = password;
        }
    }
    public static class CountTracker {
        public final int maxCount;
        public final CacheMap<String,Integer> trackCache = new CacheMap<>(10000, true);

        public CountTracker(int maxCount) {
            this.maxCount = maxCount;
        }

        public boolean checkExceedMaxCount(String key) {
            Integer iVal = trackCache.get(key);
            int newVal = (iVal != null) ? iVal + 1 : 1;
            trackCache.put(key, newVal);
            return newVal >= maxCount;
        }
        public void clear() {
            trackCache.clear();
        }
    }

    public final int TOKEN_TIMEOUT_MILLIS = 15 * 60 * 1000; // Fifteen minutes
    public final UserService userService;
    public DnCoreNodeService coreNodeService;
    public DnMailService mailService;
    public DnContentService contentService;

    // Used to prevent abuse of the web site. Our defaults are low because we are using free AWS infrastructure.
    public int maxExecsPerHour = 10000;
    public int maxActiveRequests = 3;
    public int maxFormCodeCount = 10;
    public int maxIpAddressCount = 100;

    // Tracker fields.
    public int curNumberOfExecs = 0;
    public int curHourScope = 0;
    public final AtomicInteger activeRequests = new AtomicInteger(0);
    public CountTracker formTokenTracker;
    public CountTracker ipAddressTracker;

    public AuthFormHandler(UserService userService) {
        this.userService = userService;
    }

    public void init(DnCxt cxt) throws DnException {
        var config = cxt.instanceConfig;
        // These should only be configured for testing.
        maxExecsPerHour = (int)toOptLongWithDefault(config.get("auth.maxExecsPerHour"), maxExecsPerHour);
        maxActiveRequests = (int)toOptLongWithDefault(config.get("auth.maxActiveRequests"), maxActiveRequests);
        maxFormCodeCount = (int)toOptLongWithDefault(config.get("auth.maxFormCodeCount"), maxFormCodeCount);
        maxIpAddressCount = (int)toOptLongWithDefault(config.get("auth.maxIpAddressCount"), maxIpAddressCount);
        formTokenTracker = new CountTracker(maxFormCodeCount);
        ipAddressTracker = new CountTracker(maxIpAddressCount);

        coreNodeService = DnCoreNodeService.get(cxt);
        contentService = DnContentService.get(cxt);
        mailService = DnMailService.get(cxt);
    }

    public Map<String,Object> generateFormTokenData(DnCxt cxt, String extraData) throws DnException {
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
        String ed = (extraData != null) ? ":" + extraData : "";
        String tokenValue = formAuthType + ed + "@" + dateStr + formCode + ":" + randomStr;

        // Encrypting also provides randomness for free.
        DnCoreNodeService nodeService = Objects.requireNonNull(coreNodeService);
        String formToken = nodeService.encryptString(tokenValue);

        return mMap(FM_FORM_AUTH_TYPE, formAuthType, FM_FORM_AUTH_TOKEN, formToken,
                FM_CAPTCHA_DATA, mMap(FM_FORM_AUTH_CODE, formCode,
                        "message", "Eventually the auth code will not be " +
                                "supplied in this response data, instead it will have " +
                                "to be calculated by the browser based on input from the " +
                                "captcha data."));
    }

    public FormTokenComponents getTokenComponents(DnCxt cxt, String formAuthToken) throws DnException {
        DnCoreNodeService nodeService = Objects.requireNonNull(coreNodeService);
        String tokenValue = nodeService.decryptString(formAuthToken);
        List<String> beginAndEnd = StrUtil.splitString(tokenValue, "@", 2);
        if (beginAndEnd.size() < 2) {
            throw new DnException("Form auth token is not valid.");
        }
        List<String> typeAndExtra = StrUtil.splitString(beginAndEnd.get(0), ":", 2);
        String tokenType = typeAndExtra.get(0);
        String extra = typeAndExtra.size() > 1 ? typeAndExtra.get(1) : "";

        if (!tokenType.equals(FMT_SIMPLE)) {
            throw DnException.mkInput("Form auth token is not a valid token type.");
        }
        String end = beginAndEnd.get(1);
        int index2 = end.indexOf('Z');
        if (index2 < 0) {
            throw new DnException("Date is not encoded in form token.");
        }
        String dateStr = end.substring(0, index2);
        Date d = DnDateUtil.parseDate(dateStr);
        Date now = cxt.now();
        if (now.getTime() - d.getTime() > TOKEN_TIMEOUT_MILLIS) {
            throw DnException.mkInput("Form auth token has timed out.");
        }
        return new FormTokenComponents(tokenType, end.substring(index2 + 1), d, extra);
    }

    public void sendVerifyTokenToContact(DnCxt cxt, UserContact contact, String formAuthToken,
            String formAuthCode) throws DnException {
        String contactType = contact.cType;
        String contactAddress = contact.address;
        if (!"email".equals(contactType)) {
            throw DnException.mkInput("Only a contact type of *email* is currently supported.");
        }
        if (!contactAddress.contains("@")) {
            throw DnException.mkInput("Email address requires an at ('@') symbol.");
        }
        if (isInvalidTokenCode(cxt, formAuthToken, formAuthCode)) {
            throw DnException.mkInput("The validation code is either incorrect or has expired.");
        }

        String verifyCode = computeVerifyCode(formAuthToken, contactAddress);
        sendVerifyCodeEmail(cxt, contactAddress, verifyCode);
    }

    public void sendVerifyTokenToUser(DnCxt cxt, String username, String formAuthToken, String formAuthCode)
            throws DnException {
        if (isInvalidTokenCode(cxt, formAuthToken, formAuthCode)) {
            throw DnException.mkInput("The validation code is either incorrect or has expired.");
        }
        AuthUserRow userRow = userService.queryUsername(cxt, username);
        if (userRow == null) {
            throw new DnException(String.format("Username %s is not in the system on cannot receive messages.",
                    username), DnException.NOT_FOUND);
        }
        String verifyCode = computeVerifyCode(formAuthToken, userRow.primaryId);
        sendVerifyCodeEmail(cxt, userRow.primaryId, verifyCode);
    }

    public void sendVerifyCodeEmail(DnCxt cxt, String contactAddress, String verifyCode) throws DnException {
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
        if (checkInvalidVerifyCode(cxt, formAuthToken, contactAddress, verifyCode)) {
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
                            contactAddress), null, DnException.NOT_AUTHORIZED, DnException.SYSTEM,
                            DnException.AUTH);
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

    public Map<String,Object> createLoginDataForEmulation(DnCxt cxt, String username) throws DnException {
        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        if (cxt.userProfile == null) {
            throw new DnException("Authenticated context required to generate user login data for " +
                    "emulation.");
        }
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);
        Map<String,Object> retData = mMap();
        aqh.sqlDb.withSession(cxt, () -> {
            AuthUserRow usernameRow = aqh.queryByUsername(cxt, username);
            if (usernameRow == null) {
                throw new DnException(String.format("Username %s does not exist in the system.",
                        username), DnException.NOT_FOUND);
            }
            long grantingId = cxt.userProfile.grantingUserId > 0 ? cxt.userProfile.grantingUserId :
                    cxt.userProfile.userId;
            String authId = cxt.userProfile.authId != null ? cxt.userProfile.authId : "" + grantingId;
            // Encode who is granting this token+verify that can be used to do a login.
            String extra = "" + grantingId + ":" + authId;
            Map<String,Object> formTokenData = generateFormTokenData(cxt, extra);
            String formAuthToken = getReqStr(formTokenData, FM_FORM_AUTH_TOKEN);
            String verifyCode = computeVerifyCode(formAuthToken, usernameRow.primaryId);
            retData.put(USER_ID, usernameRow.userId);
            retData.put(FM_FORM_AUTH_TOKEN, formAuthToken);
            retData.put(FM_VERIFY_CODE, verifyCode);
        });
        return retData;
    }

    /**
     * Uses a username and token+verify to do a simple login. This is a trusted login so the
     * UserSourceId will be added as a validated source.
     */
    public void loginByVerifyCode(DnCxt cxt, AuthAllUserData allData, String username,
            String passwordToChange, String formAuthToken,
            String verifyCode) throws DnException {
        VerifyLoginParams loginParams = new VerifyLoginParams(formAuthToken, verifyCode, username, passwordToChange);
        doLogin(cxt, "loginByVerifyCode", allData, loginParams);
    }

    public void loginByPassword(DnCxt cxt, AuthAllUserData allData, String username, String password,
            String formAuthToken) throws DnException {
        VerifyLoginParams loginParams = new VerifyLoginParams(formAuthToken, null, username, password);
        loginParams.checkPassword = true;
        doLogin(cxt, "loginByPassword", allData, loginParams);
    }

    /** Used to set the username and password for a user. The data that is populated into AuthAllUserData can be
     * used by the caller to create the authentication & source cookies. */
    public void setLoginDataAndCreateProfile(DnCxt cxt, AuthAllUserData allData, String username, String password,
            String formAuthToken, String verifyCode) throws DnException {
        VerifyLoginParams loginParams = new VerifyLoginParams(formAuthToken, verifyCode, username, password);
        doLogin(cxt, "setLoginData", allData, loginParams);
    }

    public void doLogin(DnCxt cxt, String tranName, AuthAllUserData allData, VerifyLoginParams loginParams)
            throws DnException {
        String formAuthToken = loginParams.formAuthToken;
        checkForMaximumRequests(cxt, formAuthToken);
        // Make sure we have a good username before proceeding.
        String username = loginParams.username;
        String verifyCode = loginParams.verifyCode;
        if (username != null) {
            checkValidUsername(username);
        } else if (loginParams.checkPassword) {
            throw DnException.mkInput("A username must be supplied when checking passwords.");
        }

        var sqlCxt = SqlTopicService.mkSqlCxt(cxt, SqlTopicConstants.AUTH_TOPIC);
        AuthQueryHolder aqh = AuthQueryHolder.get(sqlCxt);

        aqh.sqlDb.withSession(cxt, () -> {
            boolean doTran = true;
            long userId = allData.userId;
            if (username != null) {
                AuthUserRow usernameRow = aqh.queryByUsername(cxt, username);
                allData.authRow = usernameRow;
                if (userId <= 0) {
                    // Filling in userId.
                    if (usernameRow != null) {
                        userId = usernameRow.userId;
                        allData.userId = userId;
                    } else {
                        throw new DnException(String.format("Username %s could not be found.",
                                username), DnException.NOT_FOUND);
                    }
                }
                // Look for potential update conflict early so we can give a nice error to it.
                else if (usernameRow != null && usernameRow.userId != userId) {
                    String msg = (loginParams.checkPassword) ?
                            "User ID and username have mismatched data." :
                            String.format("Username %s has already been taken.", username);
                    throw new DnException(msg, DnException.CONFLICT);
                }
                if (loginParams.checkPassword) {
                    if (usernameRow == null) {
                        // This should never happen. If it does, then a positive userId was set
                        // but the username for that user does not find a row, implying mismatched data.
                        throw new DnException("Password validation did not get a user to validate against.");
                    }
                    if (usernameRow.encodedPassword == null) {
                        throw DnException.mkInput("The username is not set up to allow password validation.");
                    }
                    // We see if can bypass doing the database transaction.
                    var curSourceId = getFamiliarSourceId(cxt, allData, aqh);
                    if (curSourceId == null) {
                        // We do not trust a general user's password management to have it allow a login in
                        // all circumstances. It is why there is a separate token mechanism for admin
                        // type authentication which can be done from unfamiliar sources. Admin token passwords
                        // are expected to be longer and better protected. They also rotate out every
                        // few months and are assigned to an admin and not self-assigned.
                        throw new DnException("Login by password validation is not allowed from " +
                                "this unfamiliar browser or device.", null, DnException.NOT_AUTHORIZED,
                                DnException.SYSTEM, DnException.AUTH);
                    }

                    // Check password. Note that we are *not* trying to disguise whether the error
                    // is a missing username or an invalid password. Any logged in user can figure out
                    // which usernames exist in the system simply by trying to change their username
                    // and seeing if they succeed. If they don't then the username already exists.
                    // A more laborious version of this check can be done during initial registration as well.
                    if (!EncodeUtil.checkPassword(loginParams.password, usernameRow.encodedPassword)) {
                        throw DnException.mkInput("Password did not match.");
                    }
                    if (!curSourceId.isModified) {
                        // Nothing interesting happening, we can skip the auth tran (but we will still
                        // do the profile tran, but profile data, unlike auth data, is assumed to be sharded in heavy
                        // use environments).
                        doTran = false;
                    } else {
                        allData.sourceId = curSourceId;
                    }
                }
            }

            if (doTran) {
                aqh.executeUserTran(sqlCxt, userId, tranName, () -> {
                    allData.authRow = AuthUserRow.extract(sqlCxt.tranData);
                    if (!loginParams.checkPassword) {
                        // Verify code. Note that we are assuming primaryId is the email address
                        // we used to send the verification code.
                        if (checkInvalidVerifyCode(cxt, formAuthToken, allData.authRow.primaryId, verifyCode)) {
                            throw DnException.mkInput("Supplied verification code is invalid.");
                        }
                    }

                    // Populate or update sourceId. This inserts/updates rows in the login sources table.
                    updateSourceId(cxt, allData, aqh);
                    // Do the actual work of this method.
                    if (!loginParams.checkPassword) {
                        mergeIntoRowForTran(allData, loginParams);
                    }

                    // Carry results back into database row.
                    sqlCxt.tranData.putAll(allData.authRow.toMap());
                });
            }

        });

        // Create/load profile and set up for setting cookie.
        FormTokenComponents comps = getTokenComponents(cxt, formAuthToken);
        setLoginProfileData(allData);
        applyFormComponentsToAuth(allData.authData, comps);

        // Login source information has changed, make sure to put it into profile data store.
        allData.updateProfileSourceId = true;

        // Create initial profile record, or if it exists, sync up data from auth row.
        userService.loadProfileRecord(cxt, tranName, allData, true);
    }



    /** Updates the sourceId information in the login sources table.
     * Should only be called in a database transaction and with *authRow* in AuthAllUserData already loaded. */
    public void updateSourceId(DnCxt cxt, AuthAllUserData allData, AuthQueryHolder aqh) throws DnException {
        UserSourceId sourceId = getFamiliarSourceId(cxt, allData, aqh);
        if (sourceId == null) {
            sourceId = UserSourceId.createNew();
            sourceId.userId = allData.userId;
            sourceId.isNew = true;
        }
        sourceId.addSource(cxt, allData.ipAddress, allData.userAgent);
        if (sourceId.isNew || sourceId.isModified) {
            // Make sure we have all the related user data available for doing an update, and if user
            // fields have changed, make sure the change is picked up as well.
            sourceId.data = SqlTopicUtil.mergeUserFields(sourceId.data, allData.authRow.data);
            aqh.updateLoginSourceId(cxt, sourceId, sourceId.isNew);
            // Since we went through the work of interacting with the database, we might as well refresh
            // the cookie in the browser as well.
            sourceId.forceRegenerateCookie = true;
        }
        allData.sourceId = sourceId;
    }

    /** Sees if the source of the request comes from a browser or device that has been associated with
     * a successful login. This method is the method that justifies the rest of the code
     * that handles the *UserSourceId* object. */
    public UserSourceId getFamiliarSourceId(DnCxt cxt, AuthAllUserData allData, AuthQueryHolder aqh)
            throws DnException {
        UserSourceId sourceId = allData.sourceId;
        UserSourceId tSourceId = null; // Source ID from or for login sources tables.
        if (sourceId != null) {
            // Query for it.
            tSourceId = aqh.queryLoginSourceId(cxt, allData.userId, sourceId.sourceCode);
        }
        if (tSourceId == null) {
            String ipAddress = cxt.forwardedFor != null ? cxt.forwardedFor : CoreConstants.ND_LOCAL_IP_ADDRESS;
            List<UserSourceId> recentIds = aqh.queryRecentLoginSourceIds(cxt, allData.userId);
            tSourceId = findItem(recentIds, sId ->
                    findItem(sId.ipAddresses, ipA -> ipA.ipAddress.equals(ipAddress)) != null);
            if (tSourceId != null && sourceId != null) {
                // The cookie and the sourceId code we want to apply are in disagreement.
                tSourceId.forceRegenerateCookie = true;
            }
        }
        return tSourceId;
    }

    public static void mergeIntoRowForTran(AuthAllUserData allData, VerifyLoginParams loginParams) throws DnException {
        AuthUserRow userRow = allData.authRow;
        String username = loginParams.username;
        String password = loginParams.password;
        checkValidUsername(username);

        // Verify row supports setting a username and password.
        if (!userRow.enabled || !userRow.roles.contains(ROLE_USER) ||
                !userRow.authUserData.containsKey(CTE_CONTACTS)) {
            throw new DnException("User is not in an appropriate state to get a login assigned to it.");
        }

        if (username != null) {
            userRow.username = username;
        }
        if (password != null) {
            userRow.passwordEncodingRule = AUTH_DN_HASH;
            userRow.encodedPassword = EncodeUtil.hashPassword(password);
        }
    }

    public static void setLoginProfileData(AuthAllUserData allData) {
        AuthUserRow userRow = allData.authRow;

        UserAuthData authData = new UserAuthData();
        userRow.populateAuthData(authData);
        if (allData.sourceId != null) {
            authData.sourceId = allData.sourceId.sourceCode;
        }
        authData.determinedUserId = true;
        allData.authData = authData;
        allData.profile = authData.createProfile();
    }

    public static void applyFormComponentsToAuth(UserAuthData authData, FormTokenComponents comps) throws DnException {
        if (authData == null) {
            return;
        }
        String extra = comps.extraData;
        if (isEmpty(extra)) {
            return;
        }
        List<String> twoParts = StrUtil.splitString(extra, ":");
        if (twoParts.size() < 2) {
            return;
        }
        long grantingId = toReqLong(twoParts.get(0));
        String grantingAuthId = twoParts.get(1);
        // Form component was created directly by an admin user.
        authData.grantingUserId = grantingId;
        if (authData.authId == null) {
            authData.authId = grantingAuthId + ":" + authData.userId;
        } else if (authData.authId.indexOf(':') < 0) {
            authData.authId = grantingAuthId + ":" + authData.authId;
        }
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

    public boolean isInvalidTokenCode(DnCxt cxt, String formAuthToken, String formAuthCode) throws DnException {
        checkForMaximumRequests(cxt, formAuthToken);
        var components = getTokenComponents(cxt, formAuthToken);
        String suffix = components.suffix;
        String code = StrUtil.getToNextIndex(suffix, 0, ":");
        return !code.equals(formAuthCode);
    }

    public boolean checkInvalidVerifyCode(DnCxt cxt, String formAuthToken, String contactAddress,
            String verifyCode) throws DnException {
        checkForMaximumRequests(cxt, formAuthToken);
        String code = computeVerifyCode(formAuthToken, contactAddress);

        // Make break point friendly ternary operator.
        return !code.equals(verifyCode);
    }

    public String computeVerifyCode(String formAuthToken, String contactAddress) {
        byte[] hash = EncodeUtil.stdHashToBytes(contactAddress + formAuthToken);
        return EncodeUtil.convertToReadableChars(hash, 4);
    }


    public void checkForMaximumRequests(DnCxt cxt, String formAuthToken) throws DnException {
        try {
            // Prevent pile up.
            int numRequests = activeRequests.incrementAndGet();
            if (numRequests > 3) {
                throw new DnException("There are more than three active authentication requests occurring " +
                        "simultaneously on this node.");
            }
            synchronized (this) {
                int curHour = (int)(System.currentTimeMillis() / (3600 * 1000L));
                if (curHour != curHourScope) {
                    curHourScope = curHour;
                    curNumberOfExecs = 0;
                    formTokenTracker.clear();
                    ipAddressTracker.clear();
                } else {
                    if (formTokenTracker.checkExceedMaxCount(formAuthToken)) {
                        throw DnException.mkInput("The *formAuthToken* has been used too many times.");
                    }
                    if (!coreNodeService.checkIsInternalAddress(cxt.forwardedFor)) {
                        if (ipAddressTracker.checkExceedMaxCount(cxt.forwardedFor)) {
                            throw DnException.mkInput(String.format(
                                    "The forwarded for IP %s address has made too many login related requests.",
                                    cxt.forwardedFor));
                        }
                    }
                    if (curNumberOfExecs++ > maxExecsPerHour) {
                        throw new DnException(
                                "Too many authentication related requests have occurred in the last hour.");
                    }
                    if (curNumberOfExecs > maxExecsPerHour/4) {
                        // Slow things down.
                        LogUser.log.info(cxt,
                                "Too many auth related requests coming during this hour.");
                        SystemUtil.sleep(5000);
                    }
                }
            }
        } finally {
            activeRequests.decrementAndGet();
        }
    }
}
