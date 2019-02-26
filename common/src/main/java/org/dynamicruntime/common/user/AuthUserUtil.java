package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.request.DnServletHandler;
import org.dynamicruntime.schemadata.CoreConstants;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.util.EncodeUtil;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.user.UserConstants.AUTH_DN_HASH;

@SuppressWarnings("WeakerAccess")
public class AuthUserUtil {
    public static UserAuthData computeUserAuthDataFromToken(DnCxt cxt, UserService userService, String name,
            String tokenData, DnServletHandler servletHandler) throws DnException {

        AuthUserRow authUser = userService.queryByAdminCacheToken(cxt, name, tokenData);
        if (authUser == null) {
            throw DnException.mkInput("Authentication token is not valid.");
        }
        if (!authUser.enabled) {
            throw new DnException("Cannot do token auth against disabled user.");
        }
        UserAuthData userAuthData = servletHandler.getUserAuthData();
        if (userAuthData == null) {
            userAuthData = new UserAuthData();
            servletHandler.setUserAuthData(userAuthData);
        }
        authUser.populateAuthData(userAuthData);
        userAuthData.determinedUserId = true;
        return userAuthData;
    }

    public static AuthAllUserData mkAllUserData(long userId, DnServletHandler servletHandler) {
        String ipAddress = servletHandler.getForwardedFor();
        if (ipAddress == null) {
            ipAddress = CoreConstants.ND_LOCAL_IP_ADDRESS;
        }
        String userAgent = servletHandler.getUserAgent();
        if (userAgent == null) {
            userAgent = "none";
        }
        AuthAllUserData allData = new AuthAllUserData(userId, ipAddress, userAgent);
        allData.sourceId = servletHandler.getUserSourceId();
        return allData;
    }

    public static void updateUsernameAndPassword(AuthUserRow userRow, String username, String password)
            throws DnException {
        if (username != null) {
            checkValidUsername(username);
        }

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


    /** Utility function to set response data after doing a non-admin login. */
    public static void setLoggedInResponse(DnRequestCxt requestCxt, AuthAllUserData allData) {
        // Bind to current user and session.
        requestCxt.webRequest.setUserSourceId(allData.sourceId);
        setUpdatedProfileResponse(requestCxt, allData);
        requestCxt.mapResponse.put(AUTH_LOGGED_IN_USER, true);
    }

    public static void setUpdatedProfileResponse(DnRequestCxt requestCxt, AuthAllUserData allData) {
        requestCxt.webRequest.setUserAuthData(allData.authData);
        requestCxt.cxt.userProfile = allData.profile;

        // We will be authenticated after this request.
        requestCxt.webRequest.setAuthCookieOnResponse(true);

        // Return same data as if we had asked for profile information for ourselves.
        requestCxt.mapResponse.putAll(allData.profile.toMap());
   }

}
