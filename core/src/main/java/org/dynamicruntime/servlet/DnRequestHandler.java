package org.dynamicruntime.servlet;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.request.DnServletHandler;
import org.dynamicruntime.startup.InstanceRegistry;
import org.dynamicruntime.util.ParsingUtil;
import org.dynamicruntime.util.StrUtil;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnRequestHandler implements DnServletHandler {
    public static boolean enableLengthRounding = false;
    public static boolean logHttpHeaders = false;
    public String target;
    public String contextRoot;
    public String subTarget;
    public HttpServletRequest request;
    public HttpServletResponse response;
    public String method;
    public String uri;
    public String queryStr;
    public String contentType;
    public String logRequestUri;
    public Map<String,Object> queryParams;
    public Map<String,Object> postData;
    public String forwardedFor;
    public ContextRootRules contextRules;
    public boolean logSuccess = true;
    public boolean sentResponse = false;

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
        String ff = request.getHeader("X-Forwarded-For");
        this.forwardedFor = (ff != null && ff.length() > 0) ? ff : null;
        URLEncodedUtils.parse(queryStr, StandardCharsets.UTF_8);
        this.logRequestUri = method + ":" + uri + ((queryStr != null) ? "?" + queryStr : "");
    }

    public void handleRequest() {
        DnCxt cxt = null;
        try {
            // For now default to local instance and for getting cxt objects, eventually, we will do more.
            cxt = InstanceRegistry.createCxt("request", InstanceRegistry.defaultInstance);
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
            if (!key.toLowerCase().contains("password")) {
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
                result = "***";
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
                headers.put(name, new ArrayList<>(hdrValues));
            }
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
        InputStream in = request.getInputStream();
        byte[] bytes = IOUtils.toByteArray(in);
        return new String(bytes, StandardCharsets.UTF_8);
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
        response.setContentLength(data.length);
        var output = response.getOutputStream();
        output.write(data);
        response.flushBuffer();
    }

    public void sendStringResponse(String strResp, int code, String mimeType) throws IOException {
        var bytes = strResp.getBytes(StandardCharsets.UTF_8);
        response.setStatus(code);
        response.setContentType(mimeType);
        response.setContentLength(bytes.length);
        if (enableLengthRounding) {
            int roundToLen = 100 * ((bytes.length + 99)/100) - bytes.length;
            if (roundToLen > 0) {
                String pad = StringUtils.repeat('z', roundToLen);
                // Make analysis looking at purely the length of the response difficult (some subtle SSL attacks
                // use length analysis and time it takes to respond to extract meta information from packets).
                response.setHeader("X-Padding", pad);
            }
        }

        var output = response.getOutputStream();
        output.write(bytes);
        response.flushBuffer();
    }

    // Let auth code get cookies.
    @Override
    public List<String> getRequestHeader(String header) {
        var headers = request.getHeaders(header);
        return Collections.list(headers);
    }

    // Let auth code set cookies.
    @Override
    public void addResponseHeader(String header, String value) {
        response.addHeader(header, value);
    }

    @Override
    public void sendRedirect(String redirectUrl) throws IOException {
        response.setStatus(303);
        response.setHeader("Location", redirectUrl);
        response.flushBuffer();
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
