package org.dynamicruntime.request;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnRequestInfo {
    public final String userAgent;
    public final String forwardedFor;
    /** Whether the request is proxied. This is set if forwardedFor is non-empty; */
    public final boolean isProxied;
    /** Whether the request is from the load balancer. This is set if the user agent contains the string.
     * Elb-Health. */
    public final boolean isFromLoadBalancer;

    /** The actual query parameters for request. */
    public Map<String,Object> queryParams;
    /** The parsed post data (usually JSON). */
    public Map<String,Object> postData;

    public DnRequestInfo(String userAgent, String forwardedFor, boolean isFromLoadBalancer,
            Map<String,Object> queryParams, Map<String,Object> postData) {
        String ua = (userAgent != null && userAgent.length() > 0) ? userAgent : null;
        String xf = (forwardedFor != null && forwardedFor.length() > 0) ? forwardedFor : null;
        this.userAgent = ua;
        this.forwardedFor = xf;
        this.isProxied = xf != null;
        this.isFromLoadBalancer = isFromLoadBalancer;
        this.queryParams = queryParams;
        this.postData = postData;
    }
}
