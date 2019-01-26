package org.dynamicruntime.servlet;

import org.dynamicruntime.content.DnContentService;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnRequestCxt;
import org.dynamicruntime.request.DnRequestInfo;
import org.dynamicruntime.schemadef.DnEndpoint;
import org.dynamicruntime.schemadef.DnSchemaService;
import org.dynamicruntime.schemadef.DnSchemaValidator;
import org.dynamicruntime.schemadef.DnType;
import org.dynamicruntime.startup.ServiceInitializer;
import org.dynamicruntime.user.UserAuthHook;
import org.dynamicruntime.user.UserConstants;
import org.dynamicruntime.util.ConvertUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** This class performs top level request behavior. It allows per instance variations in behavior.
 * The top level authentication and authorization are done using the context root. For example,
 * every endpoint that shares a common context root will have the same basic security profile. Configuration
 * for this class will be supplied by other service objects from the various components that are loaded. */
@SuppressWarnings("WeakerAccess")
public class DnRequestService implements ServiceInitializer {
    public static String DN_REQUEST_SERVICE = DnRequestService.class.getSimpleName();
    public static String CONTENT_ROOT = "content";

    public final Map<String,ContextRootRules> contextRulesMap = mMapT();

    public List<String> anonRoots = mList(CONTENT_ROOT, "health", "schema");
    public List<String> adminRoots = mList("node");
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
        // Eventually we will look at our configuration and make decisions about how
        // various context roots should be handled. This will allow deployment and instance
        // configuration to control top level behaviors. It will also determine which node does
        // what type of functionality.
        for (var anonRoot : anonRoots) {
            contextRulesMap.put(anonRoot, new ContextRootRules(anonRoot, false, null));
        }
        for (var adminRoot : adminRoots) {
            contextRulesMap.put(adminRoot,
                    new ContextRootRules(adminRoot, false, UserConstants.ROLE_ADMIN));
        }
        isInit = true;
    }

    public void handleRequest(DnCxt cxt, DnRequestHandler handler) throws IOException, DnException {
        String target = handler.target;
        String contextRoot = handler.contextRoot;
        String subTarget = handler.subTarget;
        String method = handler.method;
        // Redirect top level request to current preferred location.
        if (target.equals("/")) {
            handler.sendRedirect("/" + CONTENT_ROOT + "/html/endpoints.html");
            return;
        }
        if (target.equals("/favicon.ico")) {
            contextRoot = "content";
            subTarget = "images/favicon.ico";
        }
        if (subTarget == null || subTarget.isEmpty()) {
            throw new DnException("Path is not a fully constructed endpoint.", null,
                    DnException.NOT_FOUND, DnException.SYSTEM, DnException.CODE);
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

        // Eventually there will be code here that forwards some requests to other nodes.
        // This can be for two reasons.
        // 1. The current node is a proxy node whose specific purpose is to forward to a particular
        // node using *shard* and *userId* as criteria for choosing which node in a cluster. Requests
        // to the same *userId* should tend to go to the same node so that user based caching can
        // be most effective.  The request will forward the extracted user auth data as a header so it
        // does not have to be queried for again.
        // 2. This current node does not have access to the database that can handle the request (shard
        // is used to determine this) and so the request is forwarded to one that does have access.
        // In some cases we are receiving requests that were forwarded.
        //
        // After this point, we are committed to executing the request in this node.

        // Call hook to load (or potentially create) profile record for user.
        loadProfile(cxt, handler);

        // Handle content requests.
        int code = DnException.OK;

        // First up we, handle the serving up of files.
        if (method.equals("GET") && contextRoot.equals(CONTENT_ROOT)) {
            DnContentService contentService = DnContentService.get(cxt);
            if (contentService != null) {
                var content = contentService.getContent(cxt, CONTENT_ROOT + "/" + subTarget);
                //if (handler.query)
                handler.sentResponse = true;
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
        // Eventually for certain context roots we will not log requests. Otherwise log will get filled with
        // meaningless data. The logging also takes about 0.1 to 0.2 milliseconds of time.
        if (handler.logSuccess) {
            handler.logSuccess(cxt, code);
        }
    }

    void extractAuth(DnCxt cxt, DnRequestHandler handler) throws DnException {
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

        // Get user-agent.
        String userAgent = handler.getRequestHeader("User-Agent");
        // Get X-Forwarded-By
        // Assume using AWS for now, use its user-agent for requests from load balancer.
        boolean isFromLoadBalancer = (userAgent != null && userAgent.contains("ELB-Health"));
        requestCxt.requestInfo = new DnRequestInfo(userAgent, handler.forwardedFor, isFromLoadBalancer,
                handler.queryParams, handler.postData);

        // Put in hook to do authentication. For now, do not let admin requests make it through using
        // a proxy.
        if (handler.contextRules.requiredRole != null) {
            if (requestCxt.isProxied()) {
                throw new DnException("Current acting user (if any) does not have the privilege " +
                        "to execute this request", null, DnException.NOT_AUTHORIZED, DnException.SYSTEM,
                        DnException.AUTH);
            }
        }

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
                response.put(EPR_DURATION, ConvertUtil.fmtDouble(duration));
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

        int code = requestCxt.didCreation ? DnException.OK_CREATED : DnException.OK;
        handler.sendJsonResponse(response, code);
        handler.sentResponse = true;
    }

}
