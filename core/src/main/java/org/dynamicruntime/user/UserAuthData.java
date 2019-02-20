package org.dynamicruntime.user;

import org.dynamicruntime.context.UserProfile;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** Used to support authentication/authorization functionality. Used to help build UserProfile. It holds whatever
 * fields are necessary to implement functionality, which may mean some fields are focused on very different
 * purposes. It is this class request filter hooks that do authentication/authorization. Documentation
 * on these fields can be found in other classes. */
@SuppressWarnings("WeakerAccess")
public class UserAuthData {
    /** User who switched to being current user. */
    public long grantingUserId = 0;
    /** Acting user. */
    public long userId;
    /** The owner of the user. */
    public String account;
    /** The functional and security group for the user. */
    public String userGroup;
    /** The application shard of data and nodes. */
    public String shard;
    /** Logging ID for user (can be an auth token name). */
    public String authId;
    /** Outward facing name of user, usable for doing logins. */
    public String publicName;
    /** The roles assigned to user. */
    public List<String> roles;
    /** The sourceCode for the UserSourceId object. Only set when doing to standard logins. */
    public String sourceId;
    /** Indicates whether we have determined a userId. */
    public boolean determinedUserId;

    /** The extended user data. Usually has contact information and whether they have been verified. */
    public Map<String,Object> userData;

    /** Auth rules, not yet fully defined. */
    public Map<String,Object> authRules;

    /** Last time cookie was modified (or will be modified). If this is null, then user client side
     * auth data is not involved in authentication caching. */
    public Date cookieModifiedDate;

    /** Last time profile was last updated. This is extracted from the authentication cookie. If the cookie
     * did not generated this object, then this value will be null. */
    public Date profileModifiedDate;

    public UserProfile createProfile() {
        var up = new UserProfile(userId, account, userGroup, roles);
        up.authId = authId != null ? authId : "" + userId;
        up.publicName = publicName;
        up.sourceId = sourceId;
        up.authData = userData;
        up.authRules = authRules;
        up.cookieModifiedDate = cookieModifiedDate;
        // We allow cookie to play back against the profile loading logic so it can determine
        // if its cache is fresh. This allows one node to change the cookie to tell other nodes
        // that the profile has changed.
        up.modifiedDate = profileModifiedDate;
        return up;
    }
}
