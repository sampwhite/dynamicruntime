package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnServletHandler;
import org.dynamicruntime.user.UserAuthData;

@SuppressWarnings("WeakerAccess")
public class AuthUserUtil {
    public static UserAuthData computeUserAuthDataFromToken(DnCxt cxt, UserService userService, String name,
            String tokenData, DnServletHandler servletHandler) throws DnException {

        AuthUserRow authUser = userService.queryCacheToken(cxt, name, tokenData);
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
            ipAddress = "localhost";
        }
        String userAgent = servletHandler.getUserAgent();
        if (userAgent == null) {
            userAgent = "none";
        }
        AuthAllUserData allData = new AuthAllUserData(userId, ipAddress, userAgent);
        allData.sourceId = servletHandler.getUserSourceId();
        return allData;
    }
}
