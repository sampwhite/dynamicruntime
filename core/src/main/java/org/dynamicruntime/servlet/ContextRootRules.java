package org.dynamicruntime.servlet;

/** Placeholder class for doing proxying and specifying whether certain security needs to be applied. */
@SuppressWarnings("WeakerAccess")
public class ContextRootRules {
    public String contextRoot;
    /** Whether a login type authentication is always needed to perform the request. In some scenarios,
     * if the origination request does not go through a load balancer and is from a trusted IP address,
     * it will be allowed to make the request if the value of *needsLogin* is false. */
    public boolean needsLogin;
    public String requiredRole;

    public ContextRootRules(String contextRoot, boolean needsLogin, String requiredRole) {
        this.contextRoot = contextRoot;
        this.needsLogin = needsLogin;
        this.requiredRole = requiredRole;
    }
}
