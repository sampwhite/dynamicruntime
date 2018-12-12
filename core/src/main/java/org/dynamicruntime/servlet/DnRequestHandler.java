package org.dynamicruntime.servlet;

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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class DnRequestHandler implements DnServletHandler {
    public static boolean enableLengthRounding = false;
    public String target;
    public String contextRoot;
    public String subTarget;
    public HttpServletRequest request;
    public HttpServletResponse response;
    public String method;
    public String uri;
    public String queryStr;
    public String logRequestUri;
    public Map<String,Object> queryParams;
    public Map<String,Object> postData;
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

    public void decodeRequestData() {
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
        // We will parse json payloads later.
        postData = null;
    }

    public void logSuccess(DnCxt cxt, int code) {
        double duration = cxt.getDuration();
        LogServlet.log.debug(cxt, String.format("%d Request %s (%s ms)",
                code, logRequestUri, fmtDouble(duration)));
    }

    public void handleException(DnCxt cxt, Throwable t) {
        try {
            if (!sentResponse) {
                sentResponse = true;
                int code = DnException.INTERNAL_ERROR;
                String source = DnException.SYSTEM;
                String activity = null;
                String msg = t.getMessage();
                if (t instanceof DnException) {
                    var de = (DnException)t;
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
                if (cxt != null) {
                    responseData.put("duration", durStr);
                }
                if (code == DnException.BAD_INPUT) {
                    LogServlet.log.debug(cxt,
                            String.format("%d User input on request %s was in error (%s ms). ", code,
                                    logRequestUri, durStr) + msg);
                } else if (code == DnException.NOT_FOUND) {
                    LogServlet.log.info(cxt,
                            String.format("%d Request %s had target that did not exist (%s ms). ",
                                    code, logRequestUri, durStr) + msg);
                } else {
                    LogServlet.log.error(cxt, t, String.format("%d Error for request %s (%s ms). ",
                            code, logRequestUri, durStr));
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

    @Override
    public Map<String, Object> getQueryParams() {
        return queryParams;
    }

    @Override
    public Map<String, Object> getPostData() {
        return postData;
    }
}
