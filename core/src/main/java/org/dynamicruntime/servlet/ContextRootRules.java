package org.dynamicruntime.servlet;

/** Place holder class for doing proxying and specifying whether certain security needs to be applied. */
@SuppressWarnings("WeakerAccess")
public class ContextRootRules {
    public String contextRoot;
    public boolean needsLogin;
    public String requiredRole;

    public ContextRootRules(String contextRoot, boolean needsLogin, String requiredRole) {
        this.contextRoot = contextRoot;
        this.needsLogin = needsLogin;
        this.requiredRole = requiredRole;
    }
}
