package org.dynamicruntime.request;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.schemadef.DnEndpoint;

import java.util.*;

@SuppressWarnings("WeakerAccess")
public class DnRequestCxt {
    public static final String DN_REQUEST_CXT = DnRequestCxt.class.getSimpleName();
    /** The DnCxt object attached to this request. This object is registered in the *locals* of the
     * DnCxt under the key *DN_REQUEST_CXT*. */
    public final DnCxt cxt;
    /** Acting userId, we are going to assume it is a *long* because userId is used as a the beginning part
     * of so many primary keys for a database, it makes sense that it optimize storage.  If this
     * value is zero, then the user is assumed to the system user. This attribute can be modified
     * when doing requests for authentication. */
    public long userId;
    /** Role privileges granted to user. The initial layer of security (done before the endpoint is called)
     * only determines top level privileges. Interior privileges to specific organizations or content
     * must be determined inside the endpoint function. */
    public Set<String> userRoles = new HashSet<>();

    /** The raw web request. The endpoint function should allow for this being null so that one endpoint
     * can call another endpoint locally without having to make an actual HTTP request. */
    public DnWebRequest webRequest;

    /** Schema validated and coerced request data. This is the *meat* of the request. */
    public final Map<String,Object> requestData;

    /** The endpoint definition being applied to this request. */
    public final DnEndpoint endpoint;

    /** Map part of a response. These are populated initially by the endpoint function
     * and then more fully populated by protocol handlers. */
    public Map<String,Object> mapResponse = new HashMap<>();

    /** The *items* data for a list response. */
    public List<Map<String,Object>> listResponse;

    /** Set to true, if response was sent directly bypassing normal behavior. */
    public boolean responseSent;

    public DnRequestCxt(DnCxt cxt, Map<String,Object> requestData, DnEndpoint endpoint) {
        this.cxt = cxt;
        this.requestData = requestData;
        this.endpoint = endpoint;
    }
}
