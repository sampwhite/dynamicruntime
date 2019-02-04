package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.hook.DnHookFunction;
import org.dynamicruntime.servlet.DnRequestHandler;
import org.dynamicruntime.servlet.DnRequestService;
import org.dynamicruntime.user.UserAuthCookie;
import org.dynamicruntime.user.UserAuthData;

import java.util.Date;
import java.util.List;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.user.UserConstants.*;

@SuppressWarnings("WeakerAccess")
public class UserExtractAuthFunction implements DnHookFunction<DnRequestService, DnRequestHandler> {
    // Using short cache since it is easier to test during development.
    public static final int AUTH_CACHE_TIMEOUT_SECS = 10;

    public final UserService userService;

    public UserExtractAuthFunction(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void notify(DnCxt cxt, DnRequestService parent, DnRequestHandler workData) throws DnException {
        List<String> tokens = workData.getRequestHeaders(AUTH_HDR_TOKEN);

        // We assume there is at most one token.
        String token = (tokens != null && tokens.size() > 0) ? tokens.get(0) : null;
        if (token != null) {
            // We can do token based authentication.
            int index = token.indexOf('#');
            if (index < 0) {
                throw DnException.mkConv("Authentication token is incorrectly formatted.");
            }
            String name = token.substring(0, index);
            String tokenData = token.substring(index + 1);
            AuthUserUtil.computeUserAuthDataFromToken(cxt, userService, name, tokenData, workData);
            return;
        }

        UserAuthCookie authCookie = workData.userAuthCookie;
        if (authCookie != null) {
            Date expireDate = authCookie.expireDate;
            Date now = cxt.now();
            if (now.before(expireDate)) {
                // Query for auth data from database. Eventually we may do some type of caching
                // with notifications sent by other nodes to the proxy nodes to notify when
                // the cache is invalidated. Also, certain types of high volume requests also may
                // be able to live with what is encoded in the cookie as being good enough. An example
                // may be requests for semi-static resources or client based logging.
                long userId = authCookie.userId;

                // Closer cookie modification date is to current time, the quicker we need to expire cache.
                Date cookieDate = authCookie.modifiedDate;
                int secondsDiff = (int)((now.getTime() - cookieDate.getTime())/1000);
                if (secondsDiff > AUTH_CACHE_TIMEOUT_SECS) {
                    secondsDiff = AUTH_CACHE_TIMEOUT_SECS;
                }
                AuthUserRow curAuthData = userService.queryCacheUserId(cxt, userId, secondsDiff);
                if (curAuthData != null && curAuthData.enabled) {
                    UserAuthData authData = new UserAuthData();
                    // At some point, we will look for differences between the row record and the cookie
                    // to see if transitioning is occurring.
                    authData.grantingUserId = authCookie.grantingUserId;
                    authData.userId = userId;
                    authData.publicName = curAuthData.getPublicName();
                    authData.account = curAuthData.account;
                    authData.userGroup = curAuthData.groupName;
                    authData.shard = curAuthData.shard;
                    authData.roles = curAuthData.roles;
                    authData.authId = curAuthData.authId;
                    authData.determinedUserId = true;
                    authData.cookieModifiedDate = cookieDate;
                    workData.setUserAuthData(authData);
                    // Use cookie update to tell other nodes that their user and profile caches need
                    // to be updated.
                    boolean isUserEdit = DnRequestService.USER_ROOT.equals(workData.target) &&
                            (workData.method.equals(EPH_POST) || workData.method.equals(EPH_PUT));
                    if (curAuthData.modifiedDate.after(cookieDate) || isUserEdit) {
                        workData.setAuthCookieOnResponse(true);
                    }
                } else {
                    // Proactively logout so that we do not keep asking for user records that do not exist.
                    workData.isLogout = true;
                }
            }
        }
    }
}
