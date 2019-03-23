package org.dynamicruntime.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.hook.DnHookBase;
import org.dynamicruntime.servlet.DnRequestHandler;
import org.dynamicruntime.servlet.DnRequestService;

/** The hooks used in {@link DnRequestService} to implement authentication related details of the request.
 * @see DnHookBase
 * @see org.dynamicruntime.common.user.UserService#onCreate */
public class UserAuthHook implements DnHookBase<DnRequestService, DnRequestHandler> {
    /** Parses out authentication cookie or other authentication parameters.
     * @see DnRequestService#extractAuth  */
    public static final UserAuthHook extractAuth = new UserAuthHook();
    /** Loads up the profile data for the user.
     * @see DnRequestService#loadProfile */
    public static final UserAuthHook loadProfile = new UserAuthHook();
    /** Prepares for auth cookies to be sent back.
     * @see DnRequestService#checkAddAuthCookies */
    public static final UserAuthHook prepAuthCookies = new UserAuthHook();

    // This method exists so that the stack trace in the logs can be full text searched for *UserAuthHook*.
    public boolean callHook(DnCxt cxt, DnRequestService reqService, DnRequestHandler reqHandler) throws DnException {
        return callHookImpl(cxt, reqService, reqHandler);
    }
}
