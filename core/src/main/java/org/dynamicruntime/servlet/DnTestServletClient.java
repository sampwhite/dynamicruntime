package org.dynamicruntime.servlet;

import org.dynamicruntime.context.InstanceConfig;
import org.dynamicruntime.exception.DnException;
import org.dynamicruntime.util.HttpUtil;
import org.dynamicruntime.util.ParsingUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

/**
 * Class to perform endpoint requests that are executed internally and bypassing the HTTP layer. Used
 * for testing.
 */
@SuppressWarnings("WeakerAccess")
public class DnTestServletClient {
    public final InstanceConfig instanceConfig;
    public Map<String,List<String>> curHeaders = mMapT();

    public DnTestServletClient(InstanceConfig instanceConfig) {
        this.instanceConfig = instanceConfig;
    }

    public void addHeader(String header, String value) {
        addToListMap(curHeaders, header.toLowerCase(), value);
    }

    public void setHeader(String header, String value) {
        curHeaders.put(header.toLowerCase(), mList(value));
    }

    public DnRequestHandler sendGetRequest(String endpoint, Map<String,Object> args) throws DnException  {
        String queryStr = (args != null) ? HttpUtil.encodeHttpArgs(args) : "";
        DnRequestHandler requestHandler = new DnRequestHandler(instanceConfig.instanceName,
                EPH_GET, endpoint, curHeaders);
        requestHandler.queryStr = queryStr;
        executeRequest(requestHandler);
        return requestHandler;
    }

    public DnRequestHandler sendEditRequest(String endpoint, Map<String,Object> args, Map<String,Object> data,
            boolean isPut) throws DnException {
        String method = (isPut) ? EPH_PUT : EPH_POST;
        String queryStr = (args != null) ? HttpUtil.encodeHttpArgs(args) : "";
        String postData = data != null ? ParsingUtil.toJsonString(data) : "";
        DnRequestHandler requestHandler = new DnRequestHandler(instanceConfig.instanceName,
                method, endpoint, curHeaders);
        requestHandler.queryStr = queryStr;
        requestHandler.testPostData = postData;
        executeRequest(requestHandler);
        return requestHandler;
    }

    public void extractCookies(DnRequestHandler requestHandler) {
        // Add code to add cookies to *curHeaders*.
    }

    public void executeRequest(DnRequestHandler requestHandler) throws DnException {
        requestHandler.handleRequest();
        extractCookies(requestHandler);
    }


}
