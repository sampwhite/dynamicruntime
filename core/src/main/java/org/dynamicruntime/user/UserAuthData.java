package org.dynamicruntime.user;

import org.dynamicruntime.context.UserProfile;

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
    /** Logging ID for user (can be an auth token name). */
    public String authId;
    /** The roles assigned to user. */
    public List<String> roles;
    /** Indicates whether we have determined a userId. */
    public boolean determinedUserId;

    /** The extended user data. Usually has contact information and whether they have been verified. */
    public Map<String,Object> userData;

    /** Auth rules, not yet fully defined. */
    public Map<String,Object> authRules;

    public UserProfile createProfile() {
        var up = new UserProfile(userId, account, userGroup, roles);
        up.authId = authId != null ? authId : "" + userId;
        up.authData = userData;
        up.authRules = authRules;
        return up;
    }
}
