package org.dynamicruntime.common.user;

import org.dynamicruntime.context.UserProfile;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.user.UserSourceId;

import java.util.Map;

/** Convenience class for sending in and returning useful auth related objects. It is a way for the part
 * of the code that handles cookies and the part of the code that does internal authentication to
 * communicate with each other. */
@SuppressWarnings("WeakerAccess")
public class AuthAllUserData {
    public long userId; // Mutable.
    public final String ipAddress;
    public final String userAgent;
    /** Whether the loginSources section in the user profile needs be updated. */
    public boolean updateProfileSourceId;
    /** Changes to be applied to the profile extra data. */
    public Map<String,Object> extraData;

    public AuthUserRow authRow;
    public UserSourceId sourceId;
    public UserAuthData authData;
    public UserProfile profile;

    public AuthAllUserData(long userId, String ipAddress, String userAgent) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
