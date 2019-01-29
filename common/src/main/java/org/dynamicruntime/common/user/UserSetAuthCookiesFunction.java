package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.hook.DnHookFunction;
import org.dynamicruntime.servlet.DnRequestHandler;
import org.dynamicruntime.servlet.DnRequestService;
import org.dynamicruntime.user.UserAuthCookie;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.util.DnDateUtil;

import static org.dynamicruntime.user.UserConstants.*;

import java.util.Date;

@SuppressWarnings("WeakerAccess")
public class UserSetAuthCookiesFunction implements DnHookFunction<DnRequestService, DnRequestHandler> {
    public final UserService userService;

    public UserSetAuthCookiesFunction(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void notify(DnCxt cxt, DnRequestService parent, DnRequestHandler workData) {
        if (workData.isLogout) {
            workData.addResponseCookie(AUTH_COOKIE_NAME, "cleared", cxt.now());
            return;
        }
        UserAuthData userAuthData = workData.userAuthData;
        if (userAuthData == null) {
            return;
        }
        var ac = workData.userAuthCookie;
        var oldCookie = (ac != null && ac.userId == userAuthData.userId) ? ac : null;
        boolean setIt = workData.setAuthCookie;
        if (oldCookie == null && !setIt) {
            return;
        }
        Date now = cxt.now();
        if (!setIt) {
            Date modifiedDate = oldCookie.modifiedDate;
            long dur = now.getTime() - modifiedDate.getTime();
            long totalTime = oldCookie.expireDate.getTime() - modifiedDate.getTime();
            if (dur < totalTime && dur > -2000 && (dur > 3600*1000 || dur > totalTime/2)) {
                setIt = true;
            }
        }
        if (setIt) {
            Date dateCreated = (oldCookie != null) ? oldCookie.creationDate : now;
            int renewalCount = (oldCookie != null) ? oldCookie.renewalCount + 1 : 1;
            var authCookie = new UserAuthCookie(UserAuthCookie.STD_AUTH_COOKIE_CODE,
                    UserAuthCookie.CURRENT_VERSION, userAuthData.grantingUserId, userAuthData.userId,
                    userAuthData.account, userAuthData.roles, userAuthData.authId, dateCreated);
            authCookie.publicName = userAuthData.publicName;
            authCookie.groupName = userAuthData.userGroup;
            authCookie.shard = userAuthData.shard;
            authCookie.renewalCount = renewalCount;
            authCookie.modifiedDate = now;
            // May eventually configure the number of hours. Login lasts 30 hours until renewed.
            authCookie.expireDate = DnDateUtil.addHours(now, 30);
            workData.userAuthCookie = authCookie;
            // Tell code in DnRequestService to set the cookie.
            workData.setAuthCookie = true;
        }
    }
}
