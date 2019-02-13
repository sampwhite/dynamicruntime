package org.dynamicruntime.user;

@SuppressWarnings({"WeakerAccess", "unused"})
public class UserConstants {
    // Namespace for user types.
    public static final String USER_NAMESPACE = "user";

    //
    // Auth constants.
    //
    /** The data associated with a consumer that holds contacts and granted roles. */
    public static final String AUTH_USER_DATA = "authUserData";
    /** The piece of information that can be used to uniquely find a consumer. In most cases it is the primary
     * email address, but for some types of applications it can hold additional or different data encoded into
     * the string. */
    public static final String AUTH_USER_PRIMARY_ID = "userPrimaryId";
    /** A convenience username assigned to the userId. It is unique so that different users cannot have
     * different names. If user data is being bulk-loaded, then this value may be adjusted in order to
     * make it unique. */
    public static final String AUTH_USERNAME = "username";
    /** Prefix assigned to usernames that have not yet been truly determined. The username is an entity that
     * the actual user can potentially delay filling out until user is more fully provisioned. If a username
     * starts with this prefix it is treated as if the user has no username. */
    public static final String AUTH_USERNAME_TMP_PREFIX = "@TMP@";
    /** The authId that uniquely identifies the authToken. Both are required for token authentication. */
    public static final String AUTH_ID = "authId";
    /** Token used to do authentication. Tokens come in different varieties. */
    public static final String AUTH_TOKEN = "authToken";
    /** Rules to apply to any authentication done by token. This can be used to limit what IP addresses
     * can use the token or to limit modification actions. It can also be used to designate that
     * all GET actions should go to read only secondary databases. */
    public static final String AUTH_RULES = "authRules";
    /** Name of HTTP header used to define a token parameter. */
    public static final String AUTH_HDR_TOKEN = "DnAuthToken";
    /** Name of cookie used to store authentication credentials. */
    public static final String AUTH_COOKIE_NAME = "DnAuthCookie";
    /** Boolean to indicate whether logout was done. */
    public static final String AUTH_LOGGED_OUT_USER = "loggedOutUser";

    /** Fields extracted from *authUserData*. */
    public static final String AUTH_ROLES = "roles";
    public static final String AUTH_ENCODED_PASSWORD = "encodedPassword";
    public static final String AUTH_PASSWORD_ENCODING_RULE = "passwordEncodingRules";
    public static final String AUTH_VALIDATED_CONTACTS = "validatedContacts";

    /** Standard hashing rule for password encoding. */
    public static final String AUTH_DN_HASH = "dnHash";

    //
    // Login and registration constants.
    //
    /** The type of login/registration that the browser should implement. If the login type is simple, then
     * there is no captcha and the token can be computed by a trivial javascript operation. The
     * *formAuthType* and other related fields are also used during self-registration. */
    public static final String FM_FORM_AUTH_TYPE = "formAuthType";
    /** A supplied token that must be played back with any login attempt. Tokens can only be used
     * a few times before you need to get a new one. */
    public static final String FM_FORM_AUTH_TOKEN = "formAuthToken";
    /** A code that must be supplied with the login (or registration) request. It is checked against the
     * *loginToken* using an algorithm determined by the *loginType*. A typical usage is for a captcha to provide user
     * input that then generates the code. Another option is for javascript in the browser to perform
     * a complicated hash requiring a couple of seconds browser compute time. */
    public static final String FM_FORM_AUTH_CODE = "formAuthCode";
    /** Information needed to present or execute the captcha. In some cases, the captcha data may not actually cause any
     * meaningful interaction with the browser user but there is meant to be interaction with the browser
     * itself that is at least slightly non-trivial. */
    public static final String FM_CAPTCHA_DATA = "captchaData";
    /** The verification code sent to a targeted contact address. */
    public static final String FM_VERIFY_CODE = "verifyCode";
    /** The password for a user. */
    public static final String FM_PASSWORD = "password";

    //
    // Types of logins. Only doing simple for now. In the future, this may cover oauth2 and ping federate
    // scenarios as well.
    //
    /** Simple unconstrained login. The loginCode is produced by taking the *loginCode* value out
     * of the captchaData and sending it with any login/registration action. */
    public static final String FMT_SIMPLE = "simple"; // FMT = form type

    //
    // Role constants.
    //
    /* Has privileges to do common user actions. */
    public static final String ROLE_USER = "user";
    /* Has privileges to do all administrative activities. Currently in the application this also means the
    * admin user can impersonate other users. */
    public static final String ROLE_ADMIN = "admin";
    /* Has privileges to do batch operations. For now this is just an example of what additional roles
    * we might have if this application ever grows to significant size. */
    public static final String ROLE_BATCH = "batch";

    //
    // Fields that hold contact information.
    //
    /** List of validated contacts. */
    public static String CTE_VALIDATED_CONTACTS = "validatedContacts"; // CTE = contact entry
    /** List of contacts. */
    public static String CTE_CONTACTS = "contacts";

    //
    // Contacts and source (or device identifiers).
    //
    /** A numeric counter to uniquely identify the contact. */
    public static String CT_CONTACT_ID = "contactId";
    /** The type of contact for a contact address. The two principal types are mobile and email. */
    public static String CT_CONTACT_TYPE = "contactType";
    /** The contact address. */
    public static String CT_CONTACT_ADDRESS = "contactAddress";
    /** The display version of the contact address. For example, the contact address may be all lower
     * case but the display version may have mixed case. */
    public static String CT_CONTACT_DISPLAY_ADDR = "contactDisplayAddr";
    /** The usage to which the address is used. This is primarily helpful metadata for the user. It can
     * have values such as work, home, personal, etc. */
    public static String CT_CONTACT_USAGE = "contactUsage";

    //
    // Possible contact usages. We are only doing one for now, but this can expand and evolve dependent on
    // needs of the application.
    //
    public static final String CTU_REGISTRATION = "registration"; // CTU = contact usage

    //
    // Possible contact types.
    //
    /** Contact of mobile type. */
    public static final String CTT_MOBILE = "mobile"; // CTT = contact type
    /** Contact of email type. */
    public static final String CTT_EMAIL = "email";

    /** A generated unique identifier for a login source. */
    public static final String LS_SOURCE_GUID = "sourceGuid";
    /** Information about the login source. */
    public static final String LS_SOURCE_DATA = "sourceData";
    /** List of IP addresses captured with source. */
    public static final String LS_CAPTURED_IPS = "capturedIps";
    /** Date when login source should be re-verified. */
    public static final String LS_VERIFY_EXPIRATION = "verifyExpiration";
    /** Captured IP address. */
    public static final String LS_IP_ADDRESS = "ipAddress";
    /** Capture date of an IP address. */
    public static final String LS_CAPTURE_DATE = "captureDate";
    /** List of user agents in encoded form. */
    public static final String LS_USER_AGENTS = "userAgents";


    /** Name of cookie used to capture GUID */
    public static final String LS_SOURCE_COOKIE_NAME = "DnSourceId";

    //
    // User profile
    //

    /** The preferred locale of the user. */
    public static final String UP_USER_LOCALE = "userLocale";
    /** The preferred timezone of the user as a choice of timezone city. */
    public static final String UP_USER_TIMEZONE = "userTimezone";
    /** The general user profile data, including preferences and contacts information. */
    public static final String UP_USER_DATA = "userProfileData";
    /** The login source data captured for reporting purposes. */
    public static final String UP_LOGIN_SOURCES = "loginSources";
    /** The public username for the user. */
    public static final String UP_PUBLIC_NAME = "publicName";

    //
    // Endpoint functions.
    //
    public static final String AUTH_EP_TOKEN_LOGIN = "tokenLogin";
    public static final String AUTH_EP_LOGOUT = "authLogout";
    public static final String ADMIN_USER_INFO = "adminUserInfo";
    public static final String SELF_USER_INFO = "selfUserInfo";
    public static final String AUTH_GET_FORM_TOKEN = "authGetFormToken";
    public static final String AUTH_SEND_NEW_CONTACT_VERIFY_CODE = "authSendNewContactVerifyCode";
    // This may get renamed when there are more ways to create users.
    public static final String AUTH_CREATE_INITIAL_USER = "authCreateInitialUser";
    public static final String AUTH_SET_LOGIN_DATA = "authSetLoginData";
}
