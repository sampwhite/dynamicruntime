package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.UserProfile;
import org.dynamicruntime.hook.DnHookFunction;
import org.dynamicruntime.servlet.DnRequestHandler;
import org.dynamicruntime.servlet.DnRequestService;
import org.dynamicruntime.user.UserAuthCookie;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.user.UserSourceId;
import org.dynamicruntime.util.DnDateUtil;

import static org.dynamicruntime.user.UserConstants.*;

import java.util.Date;

/** Applied to {@link org.dynamicruntime.user.UserAuthHook#prepAuthCookies}. */
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
        UserProfile userProfile = cxt.userProfile;
        if (userAuthData == null || userProfile == null || userProfile.modifiedDate == null ||
                userAuthData.userId != userProfile.userId) {
            return;
        }
        var ac = workData.userAuthCookie;
        var oldCookie = (ac != null && ac.userId == userAuthData.userId) ? ac : null;

        boolean setIt = workData.setAuthCookie;
        if (oldCookie == null && !setIt) {
            return;
        }
        Date now = cxt.now();

        // Handle the sourceId first.
        UserSourceId sourceId = workData.getUserSourceId();
        if (sourceId != null) {
            boolean updateSourceId = sourceId.forceRegenerateCookie || sourceId.isModified ||
                    (sourceId.newCookieCreateDate != null &&
                            !sourceId.newCookieCreateDate.equals(sourceId.cookieCreateDate));
            if (updateSourceId) {
                String cookieValue = sourceId.computeCookieString(cxt);
                workData.addResponseCookie(LS_SOURCE_COOKIE_NAME, cookieValue,
                        DnDateUtil.addDays(now, 400));
            }
        }

        if (!setIt) {
            // See if cookie should be refreshed.
            Date modifiedDate = oldCookie.modifiedDate;
            long dur = now.getTime() - modifiedDate.getTime();
            long totalTime = oldCookie.expireDate.getTime() - modifiedDate.getTime();
            // Using hardwired numbers. If this needs to be configured, we can
            // change the code as needed.
            if (dur < totalTime && dur > -2000 && (dur > 3600*1000 || dur > totalTime/2)) {
                setIt = true;
            }
        }

        // See if the auth cookie has evidence that the user profile has changed since the date
        // we last refreshed our cached version of the user profile data.
        Date profileModifiedDate = userProfile.modifiedDate;
        if (oldCookie != null && oldCookie.profileModifiedDate.after(profileModifiedDate) &&
            !userProfile.didForceRefresh) {
            // The user profile is out of date, so we do not trust it.
            profileModifiedDate = oldCookie.profileModifiedDate;
        }
        if (!setIt && !oldCookie.profileModifiedDate.equals(profileModifiedDate)) {
            setIt = true;
        }
        if (setIt) {
            Date dateCreated = (oldCookie != null) ? oldCookie.creationDate : now;
            int renewalCount = (oldCookie != null) ? oldCookie.renewalCount + 1 : 1;
            String sourceCode = userAuthData.sourceId;
            var authCookie = new UserAuthCookie(UserAuthCookie.STD_AUTH_COOKIE_CODE,
                    UserAuthCookie.CURRENT_VERSION, userAuthData.grantingUserId, userAuthData.userId,
                    sourceCode, userAuthData.account, userAuthData.roles, userAuthData.authId, dateCreated,
                    userProfile.modifiedDate);
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
