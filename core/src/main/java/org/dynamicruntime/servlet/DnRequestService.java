package org.dynamicruntime.servlet;

import org.dynamicruntime.content.DnContentData;
import org.dynamicruntime.content.DnContentService;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.node.DnCoreNodeService;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.request.DnRequestInfo;
import org.dynamicruntime.schemadef.DnEndpoint;
import org.dynamicruntime.schemadef.DnSchemaService;
import org.dynamicruntime.schemadef.DnSchemaValidator;
import org.dynamicruntime.schemadef.DnType;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.user.UserAuthCookie;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.user.UserAuthHook;
import org.dynamicruntime.util.DnDateUtil;

import static org.dynamicruntime.user.UserConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** This class performs top-level request behavior. It allows per instance variations in behavior.
 * The top-level authentication and authorization are done using the context root. For example,
 * every endpoint that shares a common context root has the same basic security profile. Configuration
 * for this class is supplied by other service objects from the various components that are loaded. */
@SuppressWarnings("WeakerAccess")
public class DnRequestService implements ServiceInitializer {
    public static final String DN_REQUEST_SERVICE = DnRequestService.class.getSimpleName();
    public static final String CONTENT_ROOT = "content";
    public static final String PORTAL_ROOT = "portal";
    public static final String SITE_ROOT = "site";
    public static final List<String> WEB_ROOTS = mList(CONTENT_ROOT, PORTAL_ROOT, SITE_ROOT);
    public static final String USER_ROOT = "user";

    public final Map<String,ContextRootRules> contextRulesMap = mMapT();

    public List<String> anonRoots = mList(CONTENT_ROOT, PORTAL_ROOT, SITE_ROOT,"auth", "health", "schema");
    public List<String> userRoots = mList(USER_ROOT);
    public List<String> adminRoots = mList("node", "admin");
    public DnCoreNodeService coreNode;
    public boolean isInit = false;

    public static DnRequestService get(DnCxt cxt) {
        Object obj = cxt.instanceConfig.get(DN_REQUEST_SERVICE);
        return (obj instanceof DnRequestService) ? (DnRequestService)obj : null;
    }

    @Override
    public String getServiceName() {
        return DN_REQUEST_SERVICE;
    }

    @Override
    public void checkInit(DnCxt cxt) {
        if (isInit) {
            return;
        }
        // We are node state aware.
        coreNode = Objects.requireNonNull(DnCoreNodeService.get(cxt));

        // Eventually we will look at our configuration and make decisions about how
        // various context roots should be handled. This will allow deployment and instance
        // configuration to control top-level behavior. It will also determine which node does
        // what type of functionality.
        for (var anonRoot : anonRoots) {
            contextRulesMap.put(anonRoot, new ContextRootRules(anonRoot, false, null));
        }
        for (var userRoot : userRoots) {
            contextRulesMap.put(userRoot, new ContextRootRules(userRoot, true, ROLE_USER));
        }
        for (var adminRoot : adminRoots) {
            contextRulesMap.put(adminRoot,
                    new ContextRootRules(adminRoot, false, ROLE_ADMIN));
        }
        isInit = true;
    }

    public void handleRequest(DnCxt cxt, DnRequestHandler handler) throws IOException, DnException {
        String target = handler.target;
        String contextRoot = handler.contextRoot;
        String subTarget = handler.subTarget;
        String method = handler.method;
        // Redirect top-level request to current preferred location.
        if (target.equals("/") || target.equals("/logout")) {
            String redirectUrlSuffix;
            if (target.equals("/logout")) {
                handler.setIsLogout(true);
                // Add cookies based on our logged out status.
                checkAddAuthCookies(cxt, handler);
                redirectUrlSuffix = "/html/login.html";
            } else {
                redirectUrlSuffix = "/html/endpoints.html";
            }
            handler.sendRedirect("/" + CONTENT_ROOT + redirectUrlSuffix);
            handler.logSuccess(cxt, handler.rptStatusCode);
            return;
        }
        if (target.equals("/favicon.ico")) {
            contextRoot = "content";
            subTarget = "images/favicon.ico";
        }
        if (subTarget == null || subTarget.isEmpty()) {
            if (contextRoot.equals(PORTAL_ROOT)) {
                subTarget = "index.html";
            } else {
                throw new DnException("Path is not a fully constructed endpoint.", null,
                        DnException.NOT_FOUND, DnException.SYSTEM, DnException.CODE);
            }
        }
        ContextRootRules contextRules = contextRulesMap.get(contextRoot);
        if (contextRules == null) {
            throw new DnException("Path does not target one of the supported context roots.", null,
                    DnException.NOT_FOUND, DnException.SYSTEM, DnException.CODE);

        }
        handler.contextRules = contextRules;

        // Request is legitimate enough to decode the request data in the query string parameters
        // and in the JSON request body (if one is provided).
        handler.decodeRequestData();

        // Call hook to fill in initial user information.
        extractAuth(cxt, handler);

        // Apply security rules to context rules. Note that this security check is done before
        // we forward to other nodes. This means other non-proxy nodes do not have to worry about
        // basic security issues, simplifying their implementations to focus purely on their
        // own concerns.
        String rRole = handler.contextRules.requiredRole;
        UserAuthData authData = handler.userAuthData;
        boolean allowed = (rRole == null);
        if (rRole != null && authData != null && authData.determinedUserId) {
            List<String> userRoles = handler.userAuthData.roles;
            if (userRoles != null) {
                if (userRoles.contains(ROLE_ADMIN)) {
                    allowed = true;
                }
                else if (userRoles.contains(rRole)) {
                    // Paths with the word *self* in them are assumed to return data
                    // based on the user data attached to the DnCxt object, so they do not need a userId.
                    if (rRole.equals(ROLE_USER) && !target.contains("/self/")) {
                        // We have made the decision to force requests that are at the *user* level
                        // to provide a *userId*. Others may choose to automatically supply it from
                        // the authentication data. If you choose this approach, then change this
                        // code to fill in a userId here.

                        // Need to make sure userId parameter matches the userId in the authentication data.
                        Long id = getOptLong(handler.queryParams, USER_ID);
                        if (id == null) {
                            id = getOptLong(handler.postData, USER_ID);
                        } else {
                            // Make sure postData cannot contaminate our request.
                            if (handler.postData != null) {
                                handler.postData.remove(USER_ID);
                            }
                        }
                        if (id == null) {
                            throw DnException.mkInput("Parameter *userId* is required for user focused requests.");
                        }
                        if (id != authData.userId) {
                            throw new DnException("User endpoints require a *userId* parameter that matches " +
                                    "the userId of the current acting user.", null, DnException.NOT_AUTHORIZED,
                                    DnException.SYSTEM, DnException.AUTH);
                        }
                    }
                    allowed = true;
                }
            }
        }
        if (!allowed) {
            // If request is being made directly to this node and it does not change state, then we let
            // it through. Otherwise, we throw an exception.
            // Temporary code to always throw an exception.
            //if (handler.isFromLoadBalancer || handler.method == null || !handler.method.equals(EPM_GET)) {
            int code = (authData != null && authData.determinedUserId) ? DnException.NOT_AUTHORIZED :
                    DnException.AUTH_NEEDED;
            String msg = (code == DnException.AUTH_NEEDED) ? "Request needs authentication." :
                    "Acting user does not have the privilege to execute this request.";
            throw new DnException(msg, null, code, DnException.SYSTEM, DnException.AUTH);
            //}
        }

        // Eventually there will be code here that forwards some requests to other nodes.
        // This can be for two reasons.
        // 1. The current node is a proxy node whose specific purpose is to forward to a particular
        // node using *shard* and *userId* as criteria for choosing which node in a cluster to target. Requests
        // to the same *userId* should tend to go to the same node so that user based caching can
        // be most effective.  The request will forward the extracted user auth data as a header so it
        // does not have to be queried for again. Note that any node that receives that request is
        // working inside a greatly simplified security model making it easier for developers creating software
        // for that node to get their solution integrated into the entire software stack. It is expected that
        // some of these nodes will *not* be written in Java and by locking down the shard, we may also
        // simplify their database configuration as well.
        // 2. This current node does not have access to the database that can handle the request (shard
        // is used to determine this) and so the request is forwarded to one that does have access.
        // In some cases, we are receiving requests that were forwarded.
        //
        // After this point, we are no longer executing code that is shared with the proxy. The code that
        // follows is to help handle the request in *this* node.

        // Call hook to load (or potentially create) profile record for user.
        loadProfile(cxt, handler);

        // Handle content requests.
        int code = DnException.OK;

        // First up, we handle the serving up of files.
        if (method.equals("GET") && WEB_ROOTS.contains(contextRoot)) {
            DnContentService contentService = DnContentService.get(cxt);
            if (contentService != null) {
                DnContentData content;
                switch (contextRoot) {
                    case SITE_ROOT:
                        content = contentService.getSiteContent(cxt, subTarget);
                        break;
                    case PORTAL_ROOT:
                        content = contentService.getPortalContent(cxt, handler.queryParams);
                        break;
                    default:
                        content = contentService.getContent(cxt, CONTENT_ROOT + "/" + subTarget);
                }

                handler.sentResponse = true;
                checkAddAuthCookies(cxt, handler);
                if (content.immutable) {
                    handler.setResponseHeader("Cache-Control",
                            "public, immutable, max-age=3153600");
                    handler.setResponseHeader("Etag", DnRequestHandler.ETAG_NEVER_CHANGES);
                }
                if (content.isBinary) {
                    handler.sendBinaryResponse(content.binaryContent, code, content.mimeType);
                } else {
                    handler.sendStringResponse(content.strContent, code, content.mimeType);
                }
            }
        }

        // Next handle endpoints.
        if (!handler.sentResponse) {
            var schemaStore = cxt.getSchema();
            DnEndpoint endpoint = schemaStore.endpoints.get(target);
            if (endpoint != null && endpoint.method.equals(method)) {
                executeEndpoint(cxt, handler, endpoint);
            }
        }

        if (!handler.sentResponse) {
            throw new DnException("Path had no endpoint handler.", null, DnException.NOT_FOUND, DnException.SYSTEM,
                    DnException.CODE);
        }

        // Eventually, for certain context roots, we will not log requests. Otherwise, the log will get filled with
        // meaningless data. The logging also takes about 0.1 to 0.2 milliseconds of time.
        if (handler.logSuccess) {
            handler.logSuccess(cxt, code);
        }
    }

    void extractAuth(DnCxt cxt, DnRequestHandler handler) throws DnException {
        // Check for auth cookie.
        Map<String,String> cookies = handler.getRequestCookies();
        String encryptedCookie = cookies.get(AUTH_COOKIE_NAME);
        if (encryptedCookie != null && encryptedCookie.length() > 10) {
            // A valid auth cookie.
            try {
                String decodedCookie = coreNode.decryptString(encryptedCookie);
                handler.userAuthCookie = UserAuthCookie.extract(decodedCookie);
            } catch (DnException e) {
                LogServlet.log.debug(cxt, "Failed to decrypt and unpack cookie. " + e.getFullMessage());
            }
        }

        // Call hook to fill in initial user information.
        // This allows the *core* component to be ignorant of some of the messier implementation
        // details of authentication.
        UserAuthHook.extractAuth.callHook(cxt, this, handler);
        if (cxt.userProfile == null) {
            var userAuthData = handler.userAuthData;
            if (userAuthData != null && userAuthData.determinedUserId) {
                cxt.userProfile = userAuthData.createProfile();
            }
        }
    }

    void loadProfile(DnCxt cxt, DnRequestHandler handler) throws DnException {
        // In a mature implementation, the data for the profile will be pulled most of the time from
        // cache.
        if (cxt.userProfile != null) {
            UserAuthHook.loadProfile.callHook(cxt, this, handler);
        }
    }

    public void checkAddAuthCookies(DnCxt cxt, DnRequestHandler handler) throws DnException {
        // The hook call may also set a AUTH_COOKIE_NAME cookie (if doing a logout for example).
        UserAuthHook.prepAuthCookies.callHook(cxt, this, handler);
        if (handler.setAuthCookie && handler.userAuthCookie != null) {
            var authCookie = handler.userAuthCookie;
            String cookieString = authCookie.toString();
            String cookieVal = coreNode.encryptString(cookieString);
            // Let old cookies hang out for 400 days, even if the cookie only provides a login
            // for day or so.
            Date cookieExpireDate = DnDateUtil.addDays(authCookie.modifiedDate, 400);
            handler.addResponseCookie(AUTH_COOKIE_NAME, cookieVal, cookieExpireDate);
        }
    }

    void executeEndpoint(DnCxt cxt, DnRequestHandler handler,  DnEndpoint endpoint) throws DnException {
        // Coerce data using the input schema.
        DnSchemaService schemaService = DnSchemaService.get(cxt);
        if (schemaService == null) {
            // Not doing endpoints.
            return;
        }

        // Use input type to convert and coerce data.
        DnType in = endpoint.inType;
        var validator = new DnSchemaValidator(cxt, DnSchemaValidator.REQUEST_MODE);
        var data = cloneMap(handler.queryParams);
        if (handler.postData != null )  {
             data.putAll(handler.postData);
        }
        Map<String,Object> requestData;
        try {
            requestData = validator.validateAndCoerce(in, data);
        } catch (DnException e) {
            if (DnException.CONVERSION.equals(e.activity)) {
                throw DnException.mkInput("Validation failure in request data.", e);
            } else {
                throw e;
            }
        }

        // Build up a request context.
        var requestCxt = new DnRequestCxt(cxt, requestData, endpoint);
        requestCxt.webRequest = handler;

        requestCxt.requestInfo = new DnRequestInfo(handler.userAgent, handler.forwardedFor, handler.isFromLoadBalancer,
                handler.queryParams, handler.postData);

        // Execute request.
        endpoint.endpointFunction.executeRequest(requestCxt);

        if (!handler.sentResponse) {
            prepareAndSendResponse(requestCxt, endpoint.outType, handler);
            if (!requestCxt.logRequest) {
                handler.logSuccess = false;
            }
        }
    }


    public void prepareAndSendResponse(DnRequestCxt requestCxt, DnType out, DnRequestHandler handler)
            throws DnException {
        var response = requestCxt.mapResponse;
        if (out.fieldsByName != null) {
            if (out.fieldsByName.containsKey(EPR_DURATION)) {
                double duration = requestCxt.cxt.getDuration();
                response.put(EPR_DURATION, duration);
            }
            if (out.fieldsByName.containsKey(EPR_REQUEST_URI)) {
                response.put(EPR_REQUEST_URI, handler.logRequestUri);
            }
            if (out.fieldsByName.containsKey(EPR_ITEMS)) {
               // In a list scenario.
                List<Map<String,Object>> list = (requestCxt.listResponse != null) ? requestCxt.listResponse : mList();
                int originalSize = list.size();
                Long limit = getOptLong(requestCxt.requestData, EPF_LIMIT);
                if (limit != null && list.size() > limit) {
                    list = list.subList(0, limit.intValue());
                }
                response.put(EPR_ITEMS, list);
                if (out.fieldsByName.containsKey(EPR_NUM_ITEMS)) {
                    response.put(EPR_NUM_ITEMS, list.size());
                }
                if (out.fieldsByName.containsKey(EPR_HAS_MORE)) {
                    response.put(EPR_HAS_MORE, requestCxt.hasMore);
                }
                if (out.fieldsByName.containsKey(EPR_NUM_AVAILABLE)) {
                    if (requestCxt.totalListSize < originalSize) {
                        requestCxt.totalListSize = originalSize;
                    }
                    response.put(EPR_NUM_AVAILABLE, requestCxt.totalListSize);
                }
            }
        }

        checkAddAuthCookies(requestCxt.cxt, handler);

        int code = requestCxt.didCreation ? DnException.OK_CREATED : DnException.OK;
        handler.sendJsonResponse(response, code);
        handler.sentResponse = true;
    }

}
