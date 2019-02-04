package org.dynamicruntime.user;

@SuppressWarnings("unused")
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

    /** A numeric counter to uniquely identify the contact. */
    public static String CT_CONTACT_ID = "contactId";
    /** The type of contact for a contact address. The two principal types are mobile and email. */
    public static String CT_CONTACT_TYPE = "contactType";
    /** The contact address. */
    public static String CT_CONTACT_ADDRESS = "contactAddress";

    /** Contact of mobile type. */
    public static final String CT_MOBILE = "mobile";
    /** Contact of email type. */
    public static final String CT_EMAIL = "email";

    /** A generated unique identifier for a login source. */
    public static final String LS_SOURCE_GUID = "sourceGuid";
    /** Information about the login source. */
    public static final String LS_SOURCE_DATA = "sourceData";
    /** Date when login source should be re-verified. */
    public static final String LS_VERIFY_EXPIRATION = "verifyExpiration";

    /** The preferred locale of the user. */
    public static final String UP_USER_LOCALE = "userLocale";
    /** The preferred timezone of the user as a choice of timezone city. */
    public static final String UP_USER_TIMEZONE = "userTimezone";
    /** The general user profile data, including preferences and contacts information. */
    public static final String UP_USER_DATA = "userProfileData";
    /** The public username for the user. */
    public static final String UP_PUBLIC_NAME = "publicName";

    //
    // Endpoint functions.
    //
    public static final String AUTH_EP_TOKEN_LOGIN = "tokenLogin";
    public static final String AUTH_EP_LOGOUT = "authLogout";
    public static final String ADMIN_USER_INFO = "adminUserInfo";


}
