package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.hook.DnHookFunction;
import org.dynamicruntime.servlet.DnRequestHandler;
import org.dynamicruntime.servlet.DnRequestService;
import org.dynamicruntime.user.UserAuthData;

import java.util.List;

import static org.dynamicruntime.user.UserConstants.*;

@SuppressWarnings("WeakerAccess")
public class UserExtractAuthFunction implements DnHookFunction<DnRequestService, DnRequestHandler> {
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
            AuthUser authUser = userService.queryToken(cxt, name, tokenData);
            if (authUser == null) {
                throw DnException.mkConv("Authentication token is not valid.");
            }
            if (workData.userAuthData == null) {
                workData.userAuthData = new UserAuthData();
                authUser.populateAuthData(workData.userAuthData);
                workData.userAuthData.determinedUserId = true;
            }
        }
    }
}
