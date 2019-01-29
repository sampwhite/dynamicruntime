package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnServletHandler;
import org.dynamicruntime.user.UserAuthData;

@SuppressWarnings("WeakerAccess")
public class AuthUserUtil {
    public static UserAuthData computeUserAuthDataFromToken(DnCxt cxt, UserService userService, String name,
            String tokenData, DnServletHandler servletHandler) throws DnException {

        AuthUserRow authUser = userService.queryToken(cxt, name, tokenData);
        if (authUser == null) {
            throw DnException.mkInput("Authentication token is not valid.");
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

}
