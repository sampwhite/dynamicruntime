package org.dynamicruntime.servlet;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnServletHandler;
import org.dynamicruntime.startup.InstanceRegistry;
import org.dynamicruntime.user.UserAuthCookie;
import org.dynamicruntime.user.UserAuthData;
import org.dynamicruntime.util.DnDateUtil;
import org.dynamicruntime.util.EncodeUtil;
import org.dynamicruntime.util.ParsingUtil;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@SuppressWarnings("WeakerAccess")
public class DnRequestHandler implements DnServletHandler {
    public static boolean enableLengthRounding = false;
    public static boolean logHttpHeaders = false;
    public String instance;
    public String target;
    public String contextRoot;
    public String subTarget;
    public String method;
    public String uri;
    public String queryStr;
    public String contentType;
    public String logRequestUri;
    public Map<String,Object> queryParams;
    public Map<String,Object> postData;
    private Map<String,String> cookies;
    public String forwardedFor;
    public ContextRootRules contextRules;
    public boolean logSuccess = true;
    public boolean sentResponse = false;
    public DnCxt createdCxt;

    //
    // Set when the request originates as a socket connection to this node.
    //
    public HttpServletRequest request;
    public HttpServletResponse response;

    //
    // Set for doing in-process test requests and some are set even if not doing test requests.
    //
    public String testPostData;
    /* Test and report headers use lower case keys for both request and response headers. */
    public Map<String,List<String>> testHeaders;
    public int rptStatusCode;
    public String rptResponseMimeType;
    public Map<String,List<String>> rptResponseHeaders;
    public String rptResponseData;

    /** Attributes filled or acted on by hooks. */
    public UserAuthData userAuthData;
    public UserAuthCookie userAuthCookie;
    public boolean setAuthCookie;
    public boolean isLogout;

    public DnRequestHandler(String target, HttpServletRequest request, HttpServletResponse response) {
        this.target = target;
        if (target != null && target.length() > 1) {
            int index = target.indexOf('/', 1);
            if (index < 0) {
                this.contextRoot = target.substring(1);
                this.subTarget = "";
            } else {
                this.contextRoot = target.substring(1, index);
                this.subTarget = target.substring(index + 1);
            }
        }
        this.contextRoot = StrUtil.getToNextIndex(target, 1, "/");
        this.request = request;
        this.response = response;
        this.method = request.getMethod();
        this.uri = request.getRequestURI();
        this.queryStr = request.getQueryString();
        String contentType = request.getContentType();
        if (contentType == null || contentType.length() == 0) {
            contentType = "application/none";
        }
        this.contentType = contentType;
        String ff = getRequestHeader("X-Forwarded-For");
        this.forwardedFor = (ff != null && ff.length() > 0) ? ff : null;
        computeLogRequestUri();
    }

    /** For internal testing. */
    public DnRequestHandler(String instance, String method, String target, Map<String,List<String>> headers,
            Map<String,String> cookies) {
        this.instance = instance;
        this.target = target;
        if (target != null && target.length() > 1) {
            int index = target.indexOf('/', 1);
            if (index < 0) {
                this.contextRoot = target.substring(1);
                this.subTarget = "";
            } else {
                this.contextRoot = target.substring(1, index);
                this.subTarget = target.substring(index + 1);
            }
        }
        this.contextRoot = StrUtil.getToNextIndex(target, 1, "/");
        this.method = method;
        this.uri = target;
        this.queryStr = "";
        Map<String,List<String>> convertedHdrs = mMapT();
        if (headers != null) {
            for (String key: headers.keySet()) {
                String k = key.toLowerCase();
                var v = headers.get(key);
                if (v != null) {
                    convertedHdrs.put(k, v);
                }
            }
        }
        this.testHeaders = convertedHdrs;
        this.rptResponseHeaders = mMapT();
        String ct = getRequestHeader("content-type");
        this.contentType = (ct != null) ? ct : "application/json";
        this.cookies = cookies;
        computeLogRequestUri();
    }

    void computeLogRequestUri() {
        logRequestUri = method + ":" + uri + ((queryStr != null) ? "?" + queryStr : "");
    }

    public void handleRequest() {
        DnCxt cxt = null;
        try {
            if (instance == null) {
                instance = InstanceRegistry.defaultInstance;
            }

            // For now default to local instance and for getting cxt objects, eventually, we will do more.
            cxt = InstanceRegistry.createCxt("request", instance);
            // Captured for tests.
            createdCxt = cxt;

            // Execute the request.
            var requestService = DnRequestService.get(cxt);
            if (requestService == null) {
                throw new DnException("This node cannot handle endpoint requests.", null,
                        DnException.NOT_SUPPORTED, DnException.SYSTEM, DnException.CODE);
            }
            requestService.handleRequest(cxt, this);
        } catch (Throwable t) {
            handleException(cxt, t);
        }
    }

    public void decodeRequestData() throws IOException, DnException {
        List<NameValuePair> params = URLEncodedUtils.parse(queryStr, StandardCharsets.UTF_8);
        Map<String,Object> parsed = mMapT();
        for (var param : params) {
            String n = param.getName();
            String v = param.getValue();
            Object e = parsed.get(n);
            if (e instanceof CharSequence) {
                v = e.toString() + "," + v;
            }
            parsed.put(n, v);
        }
        queryParams = parsed;
        if (contentType.startsWith("application/json") && postData == null) {
            String s = readInputStream();
            postData = ParsingUtil.toJsonMap(s);
            logRequestUri = logRequestUri + encodePostDataForLogging();
        }
    }

    public String encodePostDataForLogging() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean isFirstTime = true;
        for (var key : postData.keySet()) {
            String result;
            String k = key.toLowerCase();
            if (!k.contains("password") && !k.endsWith("token")) {
                Object v = postData.get(key);
                if (v instanceof Number || v instanceof Boolean) {
                    result = fmtObject(v);
                } else if (v instanceof String) {
                    String s = (String)v;
                    if (s.length() < 128) {
                        s = s.replace("%", "%25").replace("&", "%26")
                                .replace("=", "%3D");
                        result = s;
                    } else {
                        result = "...";
                    }
                } else if (v instanceof List) {
                    result = "[...]";
                } else if (v instanceof Map) {
                    result = "{...}";
                } else {
                    result = "#";
                }
            } else {
                result = "****";
            }
            if (!isFirstTime) {
                sb.append('&');
            }
            isFirstTime = false;
            sb.append(key);
            sb.append('=');
            sb.append(result);
        }
        sb.append('}');
        return sb.toString();
    }

    public void logSuccess(DnCxt cxt, int code) {
        double duration = cxt.getDuration();
        String logReqData = logRequestUri;
        if (logHttpHeaders) {
            Map<String, List<String>> headers = mMapT();
            var names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                var name = names.nextElement();
                var hdrValues = Collections.list(request.getHeaders(name));
                if (!name.toLowerCase().equals("cookie")) {
                    headers.put(name, new ArrayList<>(hdrValues));
                }
            }
            Map<String,String> cookies = getRequestCookies();
            List<String> cookieVals = mList();
            if (cookies.size() > 0) {
                for (var key : cookies.keySet()) {
                    var val = cookies.get(key);
                    var valLimited = StrUtil.limitStringSize(val, 16);
                    cookieVals.add(key + "=" + valLimited);
                }
            }
            headers.put("Cookies", cookieVals);
            String remoteAddr = request.getRemoteAddr();
            logReqData = remoteAddr + " " + logReqData + " " + headers.toString();
        } else {
            if (forwardedFor != null) {
                logReqData = forwardedFor + " " + logReqData;
            }
        }
        LogServlet.log.debug(cxt, String.format("%d Request %s (%s ms)",
                code, logReqData, fmtDouble(duration)));
    }

    public String readInputStream() throws IOException {
        if (request != null) {
            InputStream in = request.getInputStream();
            byte[] bytes = IOUtils.toByteArray(in);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return testPostData != null ? testPostData : "";
     }

    public void handleException(DnCxt cxt, Throwable t) {
        try {
            if (!sentResponse) {
                sentResponse = true;
                int code = DnException.INTERNAL_ERROR;
                String source = DnException.SYSTEM;
                String activity = DnException.UNSPECIFIED;
                String msg = t.getMessage();
                boolean canRetry = false;
                if (t instanceof DnException) {
                    var de = (DnException)t;
                    canRetry = de.canRetry();
                    code = de.code;
                    if (code < 400) {
                        code = DnException.INTERNAL_ERROR;
                    }
                    source = de.source;
                    activity = de.activity;
                    msg = de.getFullMessage();
                }
                double duration = (cxt != null) ? cxt.getDuration() : 0;
                String durStr = fmtDouble(duration);

                var responseData = mMap("httpCode", code, "source", source, "activity", activity,
                        "message", msg);
                if (canRetry) {
                    // If in a load balancing situation, whether proxy should call another node to do the same
                    // thing again.
                    responseData.put("canRetry", true);
                }
                if (cxt != null) {
                    responseData.put("duration", durStr);
                }
                String logRequestData = logRequestUri;
                if (forwardedFor != null) {
                    logRequestData = forwardedFor + " " + logRequestData;
                }
                if (code == DnException.BAD_INPUT) {
                    LogServlet.log.debug(cxt,
                            String.format("%d User input on request %s was in error (%s ms). ", code,
                                    logRequestData, durStr) + msg);
                } else if (code == DnException.NOT_FOUND) {
                    LogServlet.log.info(cxt,
                            String.format("%d Request %s had target that did not exist (%s ms). %s",
                                    code, logRequestData, durStr, msg));
                } else if (code == DnException.NOT_SUPPORTED && DnException.CONNECTION.equals(activity)) {
                    // We give log message a four letter acronym so we can find it easily using full text
                    // search. This is not an error because we are trying to answer the question:
                    // Can this node allow connection over the network from a particular agent for a
                    // particular task.
                    LogServlet.log.info(cxt, String.format("RDBP Request deliberately bounced (%s ms). %s",
                                durStr, msg));
                }
                else {
                    LogServlet.log.error(cxt, t, String.format("%d Error for request %s (%s ms). ",
                            code, logRequestData, durStr));
                }
                sendJsonResponse(responseData, code);
            } else {
                LogServlet.log.error(cxt, t, "During sending of response got error.");
            }
        } catch (Throwable tt) {
            LogServlet.log.error(cxt, tt, "During sending of error got error.");
        }
    }

    public void sendJsonResponse(Map<String,Object> data, int code) {
        try {
            String strResp = ParsingUtil.toJsonString(data);
            sendStringResponse(strResp, code, "application/json");
       } catch (Throwable t) {
            // Not much we can do here. This is so out of norm, we do not report it to standard logging.
            // One scenario where this happens is if VM is in the middle of being shutdown.
            System.err.println(t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    public void sendBinaryResponse(byte[] data, int code, String mimeType) throws IOException {
        response.setStatus(code);
        response.setContentType(mimeType);
        if (response != null) {
            response.setContentLength(data.length);
            var output = response.getOutputStream();
            output.write(data);
            response.flushBuffer();
        } else {
            rptResponseData = EncodeUtil.uuEncode(data);
        }
    }

    public void sendStringResponse(String strResp, int code, String mimeType) throws IOException {
        setStatusCode(code);
        setResponseContentType(mimeType);

        if  (response != null) {
            var bytes = strResp.getBytes(StandardCharsets.UTF_8);
            if (enableLengthRounding) {
                int roundToLen = 100 * ((bytes.length + 99)/100) - bytes.length;
                if (roundToLen > 0) {
                    String pad = StringUtils.repeat('z', roundToLen);
                    // Make analysis looking at purely the length of the response difficult (some subtle SSL attacks
                    // use length analysis and time it takes to respond to extract meta information from packets).
                    setResponseHeader("X-Padding", pad);
                }
            }
            response.setContentLength(bytes.length);
            var output = response.getOutputStream();
            output.write(bytes);
            response.flushBuffer();
        }
        rptResponseData = strResp;
    }

    public void setStatusCode(int code) {
        if (response != null) {
            response.setStatus(code);
        }
        rptStatusCode = code;
    }

    public void setResponseContentType(String mimeType) {
        if (response != null) {
            response.setContentType(mimeType);
        }
        rptResponseMimeType = mimeType;
    }

    public void setResponseHeader(String header, String value) {
        if (response != null) {
            response.setHeader(header, value);
        }
        if (rptResponseHeaders != null) {
            rptResponseHeaders.put(header.toLowerCase(), mList(value));
        }
    }

    @Override
    public List<String> getHeaderNames() {
        if (request != null) {
            return Collections.list(request.getHeaderNames());
        }
        return (testHeaders != null) ? new ArrayList<>(testHeaders.keySet()) : mList();
    }

    @Override
    public String getRequestHeader(String header) {
        if (request != null) {
            return request.getHeader(header);
        } else {
            var values = getRequestHeaders(header);
            return (values.size() > 0) ? values.get(0) : null;
        }
    }

    // Let auth code get cookies.
    @Override
    public List<String> getRequestHeaders(String header) {
        if (request != null) {
            var headers = request.getHeaders(header);
            return Collections.list(headers);
        } else {
            if (testHeaders != null) {
                List<String> hdrs = testHeaders.get(header.toLowerCase());
                return (hdrs != null) ? hdrs : mList();
            }
            return mList();
        }
    }

    // Let auth code set cookies.
    @Override
    public void addResponseHeader(String header, String value) {
        if (response != null) {
            response.addHeader(header, value);
        }
        if (rptResponseHeaders != null) {
            addToListMap(rptResponseHeaders, header.toLowerCase(), value);
        }
    }

    @Override
    public UserAuthData getUserAuthData() {
        return userAuthData;
    }

    @Override
    public void setUserAuthData(UserAuthData userAuthData) {
        this.userAuthData = userAuthData;
    }


    @Override
    public void setAuthCookieOnResponse(boolean setAuthCookie) {
        this.setAuthCookie = setAuthCookie;
    }

    @Override
    public void setIsLogout(boolean isLogout) {
        this.isLogout = isLogout;
    }

    @Override
   public Map<String,String> getRequestCookies() {
        if (cookies == null) {
            cookies = mMapT();
            if (request != null) {
                Cookie[] rCookies = request.getCookies();
                if (rCookies != null) {
                    for (var cookie : rCookies) {
                        String v = cookie.getValue();
                        if (v != null && v.length() > 0) {
                            cookies.put(cookie.getName(), v);
                        }
                    }
                }
            }
        }
        return cookies;
    }

    @Override
    public void addResponseCookie(String cookieName, String cookieValue, Date expireDate) {
        List<String> cookieVals = mList(cookieName + "=" + cookieValue);
        if (forwardedFor != null) {
            cookieVals.add("Secure");
        }
        cookieVals.add("HttpOnly");
        cookieVals.add("Path=/");
        if (expireDate != null) {
            String expireStr = DnDateUtil.formatCookieDate(expireDate);
            cookieVals.add("Expired=" + expireStr);
        }
        String cookieVal = String.join("; ", cookieVals);
        addResponseHeader("Set-Cookie", cookieVal);
    }

    @Override
    public void sendRedirect(String redirectUrl) throws IOException {
        setStatusCode(303);
        addResponseHeader("Location", redirectUrl);
        if (response != null) {
            response.flushBuffer();
        }
        sentResponse = true;
    }

    @Override
    public boolean hasResponseBeenSent() {
        return sentResponse;
    }

    @Override
    public void setResponseHasBeenSent(boolean beenSent) {
        sentResponse = beenSent;
    }

    @Override
    public String getTarget() {
        return target;
    }
}
