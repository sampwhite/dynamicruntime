package org.dynamicruntime.common.user;

import org.dynamicruntime.context.UserProfile;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.user.UserSourceId;

/** Convenience class for sending in and returning useful auth related objects. It is a way for the part
 * of the code that handles cookies and the part of the code that does internal authentication to
 * communicate with each other. */
@SuppressWarnings("WeakerAccess")
public class AuthAllUserData {
    public long userId; // Mutable.
    public final String ipAddress;
    public final String userAgent;
    public boolean updateProfileSourceId;
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
