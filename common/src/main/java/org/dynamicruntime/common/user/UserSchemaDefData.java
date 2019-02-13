package org.dynamicruntime.common.user;

import org.dynamicruntime.schemadef.*;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.schemadef.DnRawField.*;
import static org.dynamicruntime.schemadef.DnRawType.*;
import static org.dynamicruntime.schemadef.DnRawTable.*;
import static org.dynamicruntime.schemadef.DnRawEndpoint.*;
import static org.dynamicruntime.common.user.UserTableConstants.*;

@SuppressWarnings("WeakerAccess")
public class UserSchemaDefData {
    //
    // AuthUser - Holds facts necessary to do authentication and assign userGroup and shard.
    //

    static public DnRawField userId = mkReqField(USER_ID, "User Id",
            "The global unique counter value assigned to this user in this application instance.")
                .setTypeRef(DNT_COUNT);
    static public DnRawField userGroup = mkReqField(USER_GROUP, "User Group",
            "The logical group to which the user belongs and which determines the user's shard. " +
                    "The usage of this entity varies by application.");
    static public DnRawField userShard = mkField(USER_SHARD, "User Shard",
            "The software shard for data storage to which the user is assigned. Requests can " +
                    "potentially be forwarded or redirected to a node that is dedicated to the shard.");
    static public DnRawField userAccount = mkReqField(USER_ACCOUNT, "User Account",
            "The account to which the consumer belongs to. The account can " +
                    "dictate login approach used and the enabling or disabling of various options.");
    static public DnRawField userPrimaryId = mkReqField(AUTH_USER_PRIMARY_ID, "Primary User Identification Data",
            "The data about the user that can be used to uniquely find the user. In the default " +
                    "implementation this is the primary email address.");
    static public DnRawField username = mkReqField(AUTH_USERNAME, "Username",
            "The user's preferred name when showing the name to others. It must be unique in the " +
                    "database table. It can be used for doing simplified logins once the user has been provisioned.");
    static public DnRawField authUserData = mkReqField(AUTH_USER_DATA, "Auth User Data",
            "Auth user data including contacts, passwords, and role grants encoded as a string.")
            .setTypeRef(DNT_MAP);

    static public DnRawTable authUserTable = mkStdTable(UT_TB_AUTH_USERS,
            "Main table for authenticating consumers.",
            mList(userId, userGroup, userAccount, userShard, userPrimaryId, username, authUserData), null)
            .setCounterField(USER_ID)
            .setTopLevel()
            .setComplexIndexes(mList(
                    mkComplexIndex(null, mList(AUTH_USER_PRIMARY_ID), mMap(TBI_UNIQUE_INDEX, true)),
                    mkComplexIndex(null, mList(AUTH_USERNAME), mMap(TBI_UNIQUE_INDEX, true)),
                    mkComplexIndex(null, mList(USER_ACCOUNT, MODIFIED_DATE), null),
                    mkComplexIndex(null, mList(USER_GROUP, MODIFIED_DATE), null)
            ));

    //
    // AuthContacts - Holds contacts used during the authentication process. This table is currently not
    // being used, but in certain types of applications it can be useful.
    //
    static public DnRawField contactId = mkReqField(CT_CONTACT_ID, "Contact Id",
            "An incrementing counter uniquely identifying a contact when combined with the userId.")
                .setTypeRef(DNT_COUNT).setAttribute(DN_IS_AUTO_INCREMENTING, true);
    static public DnRawField contactType = mkReqField(CT_CONTACT_TYPE, "Contact Type",
            "Auth contact type, either *mobile* or *email*.");
    static public DnRawField contactAddress = mkReqField(CT_CONTACT_ADDRESS, "Contact Address",
            "The normalized address for the contact. Either a phone number if mobile, or an email address if " +
                    "an email.");
    static public DnRawField contactAuthCode = mkField(AUTH_CODE, "Contact Auth Code",
            "An auth code to be played back by the user.");
    static public DnRawField contactAuthExpiration = mkDateField(AUTH_ACTION_EXPIRATION,
            "Contact Verification Expiration Date", "The date by which a user must get back with " +
                    "an auth code.");
    static public DnRawField contactVerified = mkReqBoolField(VERIFIED, "Contact Verified",
            "Whether a communication has been sent to the contact address and the sent information was " +
                    "used to verify that the address is a working address.");
    @SuppressWarnings("unused")
    static public DnRawTable authContactTable = mkStdUserTable(UT_TB_AUTH_CONTACTS,
            "Contacts used to do authentication.",
            mList(contactId, contactType, contactAddress, contactAuthCode, contactAuthExpiration, contactVerified),
            mList(USER_ID, CT_CONTACT_ADDRESS)/* Primary Key, note contactId is *not* the primary key. */)
            .setSimpleIndexes(mList(mList(CT_CONTACT_ID),mList(USER_ID, AUTH_ACTION_EXPIRATION, AUTH_CODE)));

    //
    // AuthLoginSources - Location from where successful password validation originated.
    //
    static public DnRawField sourceGuid = mkReqField(LS_SOURCE_GUID, "Login Source GUID",
            "A generated unique identifier attached to the requesting agent. For browsers, the GUID is " +
                    "a cookie set on the browser.");
    static public DnRawField sourceData = mkReqField(LS_SOURCE_DATA, "Login Source Data",
            "Any captured information about the login source.").setTypeRef(DNT_MAP);
    static public DnRawField sourceAuthCode = mkField(AUTH_CODE, "Source Auth Code",
            "Auth code assigned to the source which is then sent to the user by a verified contact " +
                    "address that is trusted.");
    static public DnRawField sourceAuthExpiration = mkDateField(AUTH_ACTION_EXPIRATION,
            "Login Source Code Expiration", "The expiration date on the auth code sent to " +
                    "the user.");
    static public DnRawField sourceVerified = mkReqBoolField(VERIFIED, "Login Source Verified",
            "Whether the login source has been verified as trusted.");
    static public DnRawField verifyExpiration = mkDateField(LS_VERIFY_EXPIRATION, "Verify Expiration",
            "Date when verification for login source needs to be verified.");
    static public DnRawTable authLoginSourcesTable = mkStdUserTable(UT_TB_AUTH_LOGIN_SOURCES,
            "Sources or devices from which logins originate.",
            mList(sourceGuid, sourceData, sourceAuthCode, sourceAuthExpiration, sourceVerified, verifyExpiration),
            mList(USER_ID, LS_SOURCE_GUID))
            .setSimpleIndexes(mList(mList(LS_SOURCE_GUID), mList(USER_ID, MODIFIED_DATE)));

    //
    // AuthTokens - Tokens that are used by batch and test scripts to do simple authentication as a user,
    // usually an administrative user. The number of rows in this table should be small.
    //
    static public DnRawField authId = mkReqField(AUTH_ID, "Authentication Id",
            "Unique identifier of token to apply.");
    static public DnRawField authToken = mkReqField(AUTH_TOKEN, "Authentication Token",
            "Hash of token that can be supplied to act as a particular user.");
    static public DnRawField userIdToken = mkReqIntField(USER_ID, "User ID",
            "The user ID associated with the token.");
    static public DnRawField rules = mkField(AUTH_RULES, "Auth Rules",
            "Rules to apply to actions that can be taken by token and to limit access points.")
            .setTypeRef(DNT_MAP);
    static public DnRawField expireDate = mkReqDateField(EXPIRE_DATE, "Expiration Date",
        "Date when token is no longer valid.");
    static public DnRawTable authTokensTable = mkStdTable(UT_TB_AUTH_TOKENS,
            "Authentication tokens to used for automated authenticated activity.",
            mList(authId, authToken, userIdToken, rules, expireDate),
            mList(AUTH_ID));

    //
    // UserProfiles - User profile information.
    //
    static public DnRawField userLocale = mkField(UP_USER_LOCALE, "User Locale",
            "The user's preferred locale for language and presentation of UI.");
    static public DnRawField userTimezone = mkField(UP_USER_TIMEZONE, "User Timezone",
            "The user's preferred timezone for presentation and entering of date information.");
    static public DnRawField userData = mkField(UP_USER_DATA, "User Data",
            "General user profile data including preference and contact information.").setTypeRef(DNT_MAP);
    static public DnRawTable userProfileTable = mkStdUserTable(UT_TB_USER_PROFILES,
            "User profile information for each user.",
            mList(userLocale, userTimezone, userData),
            mList(USER_ID))
            .setTopLevel();

    static public DnRawField authTokenPwd = mkReqField(AUTH_TOKEN, "Authentication Token",
            "Authentication token for doing token based login.")
            .setAttribute(EP_IS_PASSWORD, true);
    static public DnRawType tokenLoginRequest = mkType("AuthTokenLoginRequest", mList(authId, authTokenPwd));
    static public DnRawField publicUsername = mkField(AUTH_USERNAME, "Username",
            "Currently preferred choice of username.");
    static public DnRawType tokenLoginResponse = mkType("AuthTokenLoginResponse",
            mList(authId, userId, publicUsername));
    static public DnRawEndpoint tokenLoginEndpoint = mkEndpoint(EPH_POST,"/auth/token/login", AUTH_EP_TOKEN_LOGIN,
            "Login using auth identifier and token. Response sets authentication cookie. " +
                    "Reload page to see effect of login.",
            tokenLoginRequest.name, tokenLoginResponse.name);

    //static public DnRawType authLogoutRequest = mkType("AuthLogoutRequest", mList());
    static public DnRawField userAuthId = mkField(AUTH_ID, "Authentication Id",
            "Internal identifier for user used when doing logging.");
    static public DnRawField sessionUserId = mkField(USER_ID, "User Id",
            "The internal ID for the user.").setTypeRef(DNT_COUNT);
    static public DnRawField loggedOutUser = mkReqBoolField(AUTH_LOGGED_OUT_USER, "Logged Out the User",
            "Whether there was a user to log out.");
    static public DnRawType authLogoutResponse = mkType("AuthLogoutResponse",
            mList(userAuthId, sessionUserId, publicUsername, loggedOutUser));
    static public DnRawEndpoint authLogoutEndpoint = mkEndpoint(EPH_POST,"/auth/logout", AUTH_EP_LOGOUT,
            "Logs out the current user session. Reload page to see effects.",
            DNT_NONE, authLogoutResponse.name);

    static public DnRawField userIdParam = mkField(USER_ID, "User ID", "The database row " +
            "numeric identifier for user.").setTypeRef(DNT_COUNT);
    static public DnRawField primaryIdParam = mkField(AUTH_USER_PRIMARY_ID, "Primary Identifier for User",
            "The primary key for the user, usually an email address.");
    static public DnRawField usernameParam = mkField(AUTH_USERNAME, "Username",
            "The user's chosen public identifier.");
    static public DnRawType adminUserInfoRequest = mkType("AdminUserInfoRequest",
            mList(userIdParam, primaryIdParam, usernameParam));
    static public DnRawType adminUserInfoResponse = mkType("AdminUserInfoResponse", mList())
            .setReferencedTypesWithFields(
                    mList(mkTbTypeName(UT_TB_AUTH_USERS), mkTbTypeName(UT_TB_USER_PROFILES)));
    static public DnRawEndpoint adminUserInfoEndpoint = mkSimpleListEndpoint("/admin/user/info",
            ADMIN_USER_INFO,
            "Retrieves user information using one of three different options for " +
                    "identifying the user.", adminUserInfoRequest.name, adminUserInfoResponse.name);

    /*
    USER_ID, userId, AUTH_ID, authId, USER_ACCOUNT, account, USER_GROUP, userGroup,
                AUTH_ROLES, roles, UP_PUBLIC_NAME, publicName, UP_USER_LOCALE, locale,
                UP_USER_TIMEZONE, timezone, UP_USER_DATA, profileData
     */
    static public DnRawField upAuthId = mkField(AUTH_ID, "Authentication ID",
            "User identifier for logging purposes.");
    static public DnRawField account = mkReqField(USER_ACCOUNT, "Account", "The account to which " +
            "the user belongs.");
    static public DnRawField upUserGroup = mkReqField(USER_GROUP, "User Group", "The assigned " +
            "group to which the user belongs.");
    static public DnRawField roles = mkField(AUTH_ROLES, "Roles", "The authorization roles " +
            "granted to this user.");
    static public DnRawField publicName = mkReqField(UP_PUBLIC_NAME, "Public Name",
            "The name that should be used when referring to the user externally.");
    static public DnRawField locale = mkReqField(UP_USER_LOCALE, "Locale",
            "The user's preferred locale.");
    static public DnRawField timezone = mkReqField(UP_USER_TIMEZONE, "Timezone",
            "The user's preferred timezone.");
    static public DnRawField profileData = mkField(UP_USER_DATA, "User Profile Data",
            "Extended information about the user.").setTypeRef(DNT_MAP);
    static public DnRawType selfUserInfoResponse = mkType("SelfUserInfoResponse",
            mList(userId, upAuthId, account, upUserGroup, roles, publicName, locale, timezone,
                    profileData));
    static public DnRawEndpoint selfUserInfoEndpoint = mkEndpoint(EPH_GET,"/user/self/info",
            SELF_USER_INFO,
            "Retrieves profile information for the current acting user.",
            DNT_NONE, selfUserInfoResponse.name);

    //
    //
    // Registration and login page support.
    //
    //

    static public DnRawField formAuthType = mkReqField(FM_FORM_AUTH_TYPE, "Form Auth Type",
            "The type of registration or login form that will be used for doing auth activity.");
    static public DnRawField formAuthToken = mkReqField(FM_FORM_AUTH_TOKEN, "Form Auth Token",
            "The token that should be supplied with any registration/login form posts. " +
                    "The token will timeout after a period of time (fifteen minutes being fairly standard).");
    static public DnRawField formCaptchaData = mkField(FM_CAPTCHA_DATA, "Captcha Data",
            "The data, combined with form interaction by browser user, produces the *formAuthCode*. " +
                    "In the simplest implementation, the captcha data has the code inside it.");

    static public DnRawType formAuthDataResponse = mkType("FormAuthDataResponse",
            mList(formAuthType, formAuthToken, formCaptchaData));

    static public DnRawField formContactType = mkReqField(CT_CONTACT_TYPE, "Contact Type",
            "Auth contact type, currently must be *email*.");
    static public DnRawField formContactAddress = mkReqField(CT_CONTACT_ADDRESS, "Contact Address",
            "A contact address. Currently only an email address is supported.");

    static public DnRawEndpoint getFormAuthTokenEndpoint = mkEndpoint(EPH_GET,"/auth/form/createToken",
            AUTH_GET_FORM_TOKEN,
            "Returns information used for later form submits for doing registration, login or " +
                    "any other post that requires extra sensitivity to automated attacks or cross site scripting.",
            DNT_NONE, formAuthDataResponse.name);

    static public DnRawField formAuthCode = mkReqField(FM_FORM_AUTH_CODE, "Form Auth Code",
            "The browser determined code that must be supplied along with the *formAuthToken*.");
    static public DnRawType sendNewContactVerifyCodeRequest = mkType("SendNewContactVerifyCodeRequest",
            mList(formContactType, formContactAddress, formAuthToken, formAuthCode));
    static public DnRawType sendVerifyCodeResponse = mkType("SendVerifyCodeResponse",
            mList(formContactType, formContactAddress));
    static public DnRawEndpoint sendVerifyCodeForNewContactEndpoint = mkEndpoint(EPH_POST,"/auth/newContact/sendVerify",
            AUTH_SEND_NEW_CONTACT_VERIFY_CODE,
            "Sends a verify code to the specified new contact address.",
            sendNewContactVerifyCodeRequest.name, sendVerifyCodeResponse.name);

    static public DnRawField verifyCode = mkReqField(FM_VERIFY_CODE, "Verify Code",
            "The short code used to validate a contact address.");
    static public DnRawType createInitialUserRequest = mkType("CreateInitialUserRequest",
            mList(formAuthToken, formContactType, formContactAddress, verifyCode));
    // Returning the raw row of auth user data (minus password info).
    static public DnRawType createInitialUserResponse = mkType("CreateInitialUserResponse",
            mList()).setReferencedTypesWithFields(mList(mkTbTypeName(UT_TB_AUTH_USERS)));
    static public DnRawEndpoint createInitialUserEndpoint = mkEndpoint(EPH_PUT,"/auth/user/createInitial",
            AUTH_CREATE_INITIAL_USER,
            "Creates the initial user. This can be repeated with the same contact address if " +
                    "a username and password have not yet been associated with this user.",
            createInitialUserRequest.name, createInitialUserResponse.name);

    static public DnRawField fmUsername = mkReqField(AUTH_USERNAME, "Username",
            "The login username and the user's chosen public identifier for themselves.");
    static public DnRawField fmPassword = mkReqField(FM_PASSWORD, "Password", "User's password.")
            .setAttribute(EP_IS_PASSWORD, true);
    static public DnRawType setLoginDataRequest = mkType("SetLoginDataRequest",
            mList(formAuthToken, userId, fmUsername, fmPassword, verifyCode));
    static public DnRawEndpoint setLoginDataEndpoint = mkEndpoint(EPH_PUT,"/auth/user/setLoginData",
            AUTH_SET_LOGIN_DATA,
            "Set's the username and password for a created user and " +
                    "does a login for the user.",
            setLoginDataRequest.name, selfUserInfoResponse.name);


    static public DnRawSchemaPackage getPackage() {
        return DnRawSchemaPackage.mkPackage("UserSchema", USER_NAMESPACE,
                mList(authUserTable, authContactTable, authLoginSourcesTable, authTokensTable,
                        userProfileTable, tokenLoginRequest, tokenLoginResponse, tokenLoginEndpoint,
                        authLogoutResponse, authLogoutEndpoint, adminUserInfoRequest, adminUserInfoResponse,
                        adminUserInfoEndpoint, selfUserInfoResponse, selfUserInfoEndpoint,
                        formAuthDataResponse, getFormAuthTokenEndpoint,
                        sendNewContactVerifyCodeRequest, sendVerifyCodeResponse, sendVerifyCodeForNewContactEndpoint,
                        createInitialUserRequest, createInitialUserResponse, createInitialUserEndpoint,
                        setLoginDataRequest, setLoginDataEndpoint));
    }
}
