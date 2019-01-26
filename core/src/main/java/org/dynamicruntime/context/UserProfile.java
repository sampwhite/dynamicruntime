package org.dynamicruntime.context;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The user profile data. This data is loaded in two rounds. The first loads the auth data from the database
 * that holds userId authentication records (in some cases this first round uses a 3rd party authentication
 * solution, such as oauth2 or ping federate to auto register a user). The second round loads profile information
 * from the application's profile data. The issue is that there is a blurry line between what is part of
 * the auth record for a consumer and what is in the profile record, especially with emails and phone numbers.
 * In some cases, information is redundantly stored in both locations making updates to the profile a complicated
 * transaction.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class UserProfile {
    public static Locale defaultLocale = Locale.US;
    public static ZoneId defaultZoneId = ZoneId.of("America/New_York");

    //
    // Basic auth data.
    //
    /** The user that granted the privilege to allow us to act as *userId*. If the value is zero, it means
     * the system user did the granting. This value is captured mostly for logging and auditing purposes.*/
    public long grantingUserId = 0;
    /** Acting user, is used to populate queries that target user data. */
    public final long userId;
    /** The account that owns the user. Users *cannot* switch between accounts and have the same userId. This value
     * is used to bring in configuration information specific to an account. Generally accounts are associated
     * with a monetary revenue source and the originating reason for why the particular userId is present.
     * The default accounts are *local* (admin users) and *public* (general light weight internet users who
     * registered with the web-site). It should be noted that account configuration data can have a
     * massive effect on the behavior of the application -- this is where
     * the *dynamic runtime* approach to application development should give us a win. */
    public final String account;
    /** The application specific mechanism of breaking users up into user groups. User groups are used
     * to determine shard. They can also be used to determine privileges, data computation rules,
     * and the UI experience. The more things that the *userGroup* can do, the better -- however changes
     * in userGroup should be rare and may require an update (and/or migration) of all the user's active data set and
     * may even force the disabling of the current userId and creating a new one. The default determination
     * of the user group is to use the value for *account*, unless the account is *public* in which case the
     * default is the year (rendered as a string) of the user's creation date.*/
    public final String userGroup;
    /** User roles. */
    public final List<String> roles;
    /** Authentication ID, used for logging and identifying the user to external parties. If doing token
     * based authentication, this is the ID of the token. */
    public String authId;
    /** Login source GUID. Usually a cookie assigned to a browser to uniquely identify that browser. This is
     * present purely for logging and auditing purposes. This is also called a *deviceId*. */
    public String sourceId;

    /** The extended user data. Usually has contact information and whether they have been verified.
     * Other Java classes will interpret this information. */
    public Map<String,Object> authData;

    /** Auth rules, not yet fully defined. */
    public Map<String,Object> authRules;


    //
    // User profile data.
    //
    /** The user's locale. Can be modified or loaded late. In some cases, the locale may be specified by
     * a parameter to the call. */
    public Locale locale = defaultLocale;
    /**Timezone of user. Modifiable, may change as needed. Also can be null if it is not relevant. */
    public ZoneId timezone = defaultZoneId;
    /** Extra profile data to be extracted by other code. */
    public Map<String,Object> profileData;
    /** Other user profile data. There are other Java classes that can extract data from this object as needed.
     * The entities in this attribute should be treated as read-only. */
    public Map<String,Object> data;
    /** Session objects for this user that we might want to persist beyond the usage of a particular DnCxt.
     * Objects can come and go as needed and change as desired. */
    public final Map<String,Object> userCache = new HashMap<>();

    public UserProfile(long userId, String account, String userGroup, List<String> roles) {
        this.userId = userId;
        this.account = account;
        this.userGroup = userGroup;
        this.roles = roles;
    }
}
