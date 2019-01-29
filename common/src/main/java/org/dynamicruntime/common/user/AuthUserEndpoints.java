package org.dynamicruntime.common.user;

import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.schemadef.DnEndpointFunction;

import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;


import java.util.List;
import java.util.Objects;

import static org.dynamicruntime.schemadef.DnEndpointFunction.mkEndpoint;
import static org.dynamicruntime.util.DnCollectionUtil.mList;

@SuppressWarnings("WeakerAccess")
public class AuthUserEndpoints {
    /** Authenticates user from browser (setting auth cookie) using a token. */
    public static void authUsingToken(DnRequestCxt requestCxt) throws DnException {
        var cxt = requestCxt.cxt;
        var data = requestCxt.requestData;
        var userService = Objects.requireNonNull(UserService.get(requestCxt.cxt));
        String authId = getReqStr(data, AUTH_ID);
        String tokenData = getReqStr(data, AUTH_TOKEN);
        userService.authUsingToken(cxt, authId, tokenData, requestCxt.webRequest);
        requestCxt.webRequest.setAuthCookieOnResponse(true);
        long userId = Objects.requireNonNull(cxt.userProfile).userId;
        requestCxt.mapResponse.putAll(mMap(AUTH_ID, authId, USER_ID, userId,
                AUTH_USERNAME, cxt.userProfile.publicName));
    }

    public static void logout(DnRequestCxt requestCxt) {
        var cxt = requestCxt.cxt;
        requestCxt.webRequest.setIsLogout(true);
        var userProfile = cxt.userProfile;
        boolean didLogout = false;
        if (userProfile != null && userProfile.userId > 0) {
            didLogout = true;
            requestCxt.mapResponse.putAll(mMap(AUTH_ID, userProfile.authId, USER_ID, userProfile.userId,
                    AUTH_USERNAME, userProfile.publicName));
        }
        requestCxt.mapResponse.put(AUTH_LOGGED_OUT_USER, didLogout);

    }

    public static List<DnEndpointFunction> getFunctions() {
        return mList(
                mkEndpoint(AUTH_EP_TOKEN_LOGIN, AuthUserEndpoints::authUsingToken),
                mkEndpoint(AUTH_EP_LOGOUT, AuthUserEndpoints::logout));
    }

}
