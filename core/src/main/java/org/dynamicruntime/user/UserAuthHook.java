package org.dynamicruntime.user;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.hook.DnHookBase;
import org.dynamicruntime.servlet.DnRequestHandler;
import org.dynamicruntime.servlet.DnRequestService;

public class UserAuthHook implements DnHookBase<DnRequestService, DnRequestHandler> {
    public static final UserAuthHook extractAuth = new UserAuthHook();
    public static final UserAuthHook loadProfile = new UserAuthHook();

    // This method exists so that the stack trace in the logs can be full text searched for *UserAuthHook*.
    public boolean callHook(DnCxt cxt, DnRequestService reqService, DnRequestHandler reqHandler) throws DnException {
        return callHookImpl(cxt, reqService, reqHandler);
    }
}
