package org.dynamicruntime.common.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.context.UserProfile;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.hook.DnHookFunction;
import org.dynamicruntime.servlet.DnRequestHandler;
import org.dynamicruntime.servlet.DnRequestService;

@SuppressWarnings("WeakerAccess")
public class UserLoadProfileFunction implements DnHookFunction<DnRequestService, DnRequestHandler> {
    public final UserService userService;

    public UserLoadProfileFunction(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void notify(DnCxt cxt, DnRequestService parent, DnRequestHandler workData) throws DnException {
        if (cxt.userProfile == null) {
            return;
        }
        userService.loadProfileRecord(cxt, cxt.userProfile, false);
    }
}
