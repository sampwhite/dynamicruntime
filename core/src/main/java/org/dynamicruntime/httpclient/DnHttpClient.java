package org.dynamicruntime.httpclient;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.dynamicruntime.context.DnConfigUtil;
import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.HttpUtil;
import org.dynamicruntime.util.ParsingUtil;
import org.dynamicruntime.util.SystemUtil;

import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;
import static org.dynamicruntime.schemadata.CoreConstants.*;
import static org.dynamicruntime.util.DnCollectionUtil.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A convenience wrapper for using the Apache Http Client. In two projects we have switched to this
 * API from other APIs because the Apache api behaved better under stress conditions and gave us more
 * tuning knobs. One particular scenario has been particularly problematic: secure HTTPS connections
 * uploading files using a connection pool with keep-alive. If you have an ill-behaved router or
 * firewalls that timeout long running connections, you can get some undesired behavior. I personally
 * remember one scenario with hyper-visor machines that had a bug in their virtual DNS routers that was
 * particularly painful.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DnHttpClient implements Runnable {
    public final DnCxt bgCxt;
    public final String name;
    public final CloseableHttpClient client;
    public final boolean hasFastResponses;
    // Time to wait between retry attempts.
    public final long waitBetweenRetries;
    // If request has taken longer than this, then no longer do retries.
    public final long retryTimeout;
    // How long a request should be allowed to run until it should be proactively terminated.
    public final long totalRequestTimeout;

    public final Set<DnHttpRequest> activeRequests = new HashSet<>();

    public boolean isActive;

    public DnHttpClient(DnCxt parentCxt, String name) {
        this(parentCxt, name, true);
    }

    public DnHttpClient(DnCxt parentCxt, String name, boolean hasFastResponses) {
        this.bgCxt = parentCxt != null ? parentCxt.mkSubContext(name) : null;
        this.name = name;
        // Set timeouts.
        int multiplier = (hasFastResponses) ? 1000 : 5000;
        var config = RequestConfig.custom()
                .setConnectTimeout(2 * multiplier)
                .setConnectionRequestTimeout(multiplier)
                .setSocketTimeout(2 * multiplier).build();
        client = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
        this.hasFastResponses = hasFastResponses;
        this.waitBetweenRetries = multiplier/5;
        this.retryTimeout = 10 * multiplier;
        this.totalRequestTimeout = 20 * multiplier;
        this.isActive = true;
        // When doing development, having requests timeout can be a pain when stepping through debug, so
        // we enable timeouts only in environments that really need them.
        boolean useResponseTimeout = (parentCxt != null) && DnConfigUtil.getConfigBool(parentCxt,
                "http.useResponseTimeout", false,
                "Whether to timeout long HTTP requests initiated from this node.");
        if (useResponseTimeout) {
            // Note how we are trying to embed useful information into the thread name.
            // This code is written by a programmer who has looked at a lot of Java stack dumps.
            String threadName = SystemUtil.createThreadName(parentCxt, name + "HttpMonitor");
            var bgThread = new Thread(this, threadName);
            bgThread.setDaemon(true);
            bgThread.start();
        }
    }

    public DnHttpRequest doGet(DnCxt cxt, String uri, Map<String,Object> args) throws DnException {
        DnHttpRequest request = new DnHttpRequest(cxt, EPH_GET, uri);
        request.args = args;
        execute(request);
        return request;
    }

    public DnHttpRequest doPost(DnCxt cxt, String uri, Map<String,Object> values) throws DnException {
        return doEdit(cxt, uri, values, false);
    }

    public DnHttpRequest doPut(DnCxt cxt, String uri, Map<String,Object> values) throws DnException {
        return doEdit(cxt, uri, values, true);
    }

    public DnHttpRequest doEdit(DnCxt cxt, String uri, Map<String,Object> values, boolean isPut) throws DnException {
        String method = (isPut) ? EPH_PUT : EPH_POST;
        DnHttpRequest request = new DnHttpRequest(cxt, method, uri);
        request.values = values;
        execute(request);
        return request;
    }

    public void execute(DnHttpRequest request) throws DnException {
        try {
            var hReq = createHttpRequest(request.method, request.uri, request.args);
            if (request.username != null && request.authType.equals("Basic")) {
                UsernamePasswordCredentials upAuth = new UsernamePasswordCredentials(request.username,
                        request.password);
                hReq.addHeader(new BasicScheme().authenticate(upAuth, hReq, null));
            }
            if (request.cxt != null) {
                // When doing inter-node calls, pass along execution paths so that we can
                // chase down the cross node call stack.
                hReq.addHeader(NDH_HDR_REQUEST_PATH, request.cxt.getExePath());
            }
            if (hReq instanceof HttpEntityEnclosingRequest && request.values != null) {
                HttpEntityEnclosingRequest hEntity = (HttpEntityEnclosingRequest) hReq;
                var vals = request.values;
                if (vals.size() > 0) {
                    if (request.useFormEncoded) {
                        List<NameValuePair> params = new ArrayList<>();
                        for (String k : vals.keySet()) {
                            String v = HttpUtil.fmtArg(vals.get(k));
                            params.add(new BasicNameValuePair(k, v));
                        }
                        hEntity.setEntity(new UrlEncodedFormEntity(params));
                    } else {
                        hReq.setHeader("Accept", "application/json");
                        hReq.setHeader("Content-type", "application/json");
                        String json = ParsingUtil.toJsonString(vals);
                        hEntity.setEntity(new StringEntity(json));
                    }
                }
            }
            synchronized (activeRequests) {
                request.activeRequest = hReq;
                activeRequests.add(request);
            }

            // In the past, there has been profit in attempting to connect more than once, but not more
            // than once.
            int maxAttempts = 2;
            CloseableHttpResponse resp;
            while (true) {
                try {
                    request.numAttempts++;
                    resp = client.execute(hReq);
                    request.response = resp;
                    break;
                } catch (IOException io) {
                    if (request.numAttempts < maxAttempts &&
                            request.duration(new Date()) < retryTimeout) {
                        Thread.sleep(waitBetweenRetries);
                    } else {
                        throw new DnException("Failed to execute " + request + " after " + maxAttempts + " attempts.",
                                io, DnException.INTERNAL_ERROR, DnException.NETWORK, DnException.CONNECTION);
                    }
                }
            }
            var line = resp.getStatusLine();
            if (line == null) {
                throw new DnException("Could not get status line for response to request " + request + ".");
            }
            int code = line.getStatusCode();
            request.respCode = code;
            boolean isSuccess = (code == 200 || code == 201);
            if (!request.isBinary && ((code >= 200 && code < 300) || (code >= 400 && code <= 503))) {
                InputStream in = null;
                try {
                    in = resp.getEntity().getContent();
                    request.responseStr = IOUtils.toString(in, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new DnException("Failed to read response from " + request + ".",
                            e, DnException.INTERNAL_ERROR, DnException.NETWORK, DnException.IO);
                } finally {
                    if (in != null) {
                        SystemUtil.close(in);
                    }
                }
                if (request.hasJsonResponse) {
                    try {
                        request.responseData = ParsingUtil.toJsonMap(request.responseStr);
                    } catch (DnException e) {
                        isSuccess = false;
                        request.responseData = mMap("httpCode", code, "msg", request.responseStr,
                                "exception", e.getFullMessage());
                    }
                }
            }
            request.isSuccess = isSuccess;
        } catch (AuthenticationException ae) {
            throw new DnException("Cannot execute " + request + " because of authentication issue.", ae);
        } catch (UnsupportedEncodingException ue) {
            throw new DnException("Cannot execute " + request +
                    " because of data that could not be encoded.", ue);
        } catch (InterruptedException ie) {
            throw new DnException(
                    String.format("Interrupted while executing %s.", request.toString()),
                    ie, DnException.INTERNAL_ERROR, DnException.NETWORK, DnException.INTERRUPTED);
        } finally {
            synchronized (activeRequests) {
                activeRequests.remove(request);
            }
            if (request.response != null) {
               SystemUtil.close(request.response);
            }
        }
    }

    public HttpRequestBase createHttpRequest(String method, String uri, Map<String,Object> args) throws DnException {
        String u;
        if (args != null && args.size() > 0) {
            String argsStr = HttpUtil.encodeHttpArgs(args);
            if (uri.indexOf('?') > 0) {
                u = uri + "&" + argsStr;
            } else {
                u = uri + "?" + argsStr;
            }
        } else {
            u = uri;
        }
        switch (method) {
            case EPH_GET:
                return new HttpGet(u);
            case EPH_POST:
                return new HttpPost(u);
            case EPH_PUT:
                return new HttpPut(u);
            default:
                throw new DnException("Request to " + u + " is using illegal method " + method + ".");
        }
    }

    public void close() {
        isActive = false;
        synchronized (this) {
            this.notify();
        }
        SystemUtil.close(client);
    }

    /**
     * Monitors state of requests, reporting to log periodically and timing out long requests.
     */
    @Override
    public void run() {
        long lastReport = getReportTime();

        while (true) {
            synchronized (this) {
                try {
                    this.wait(5000);
                } catch (Exception ignore) {
                }
                if (!isActive) {
                    return;
                }
            }

            List<DnHttpRequest> requestsToTerminate = mList();
            Date now = new Date();
            int count;
            synchronized (activeRequests) {
                count = activeRequests.size();
                for (var req : activeRequests) {
                    if (req.duration(now) > totalRequestTimeout) {
                        // Clone so we do not have any thread issues.
                        requestsToTerminate.add(req.cloneRequest());
                    }
                }
            }
            long newLastReport = getReportTime();
            if (newLastReport != lastReport) {
                LogHttp.log.debug(bgCxt, "HttpClient " + name +
                        " currently has " + count + " active connections.");
                lastReport = newLastReport;
            }

            for (var req : requestsToTerminate) {
                if (req.activeRequest != null) {
                    try {
                        req.activeRequest.abort();
                        // Note that this type of log information is not typically available in more
                        // *framework* based solutions.
                        LogHttp.log.error(bgCxt, null, "Timed out request " + req.toString() + ".");
                    } catch (Throwable t) {
                        LogHttp.log.error(bgCxt, t, "Error aborting " + req.toString() + ".");
                    }
                } else {
                    LogHttp.log.error(bgCxt, null,
                            "Timed out non-existent request " + req.toString() + ".");
                }
            }
        }
    }

    /** Report every five minutes. */
    long getReportTime() {
        return System.currentTimeMillis()/(300*1000L);
    }
}
