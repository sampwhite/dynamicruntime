package org.dynamicruntime.request;

import java.util.Map;

/** Holds raw web request information. In the future it may also hold objects that can send direct
 * responses. */
public class DnWebRequest {
    public final Map<String,String> requestParams;
    public final Map<String,Object> postData;

    public DnWebRequest(Map<String,String> requestParams, Map<String,Object> postData) {
        this.requestParams = requestParams;
        this.postData = postData;
    }
}
