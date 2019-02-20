package org.dynamicruntime.servlet;

import org.dynamicruntime.context.InstanceConfig;
import org.dynamicruntime.util.HttpUtil;
import org.dynamicruntime.util.ParsingUtil;

import java.util.List;
import java.util.Map;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.schemadef.DnSchemaDefConstants.*;

/**
 * Class to perform endpoint requests that are executed internally and bypassing the HTTP layer. Used
 * for testing and simulations.
 */
@SuppressWarnings({"WeakerAccess","unused"})
public class DnTestServletClient {
    public final InstanceConfig instanceConfig;
    public Map<String,List<String>> curHeaders = mMapT();
    public Map<String,String> cookies = mMapT();

    public DnTestServletClient(InstanceConfig instanceConfig) {
        this.instanceConfig = instanceConfig;
    }

    public void addHeader(String header, String value) {
        addToListMap(curHeaders, header.toLowerCase(), value);
    }

    public void setHeader(String header, String value) {
        curHeaders.put(header.toLowerCase(), mList(value));
    }

    public DnRequestHandler sendGetRequest(String endpoint, Map<String,Object> args)  {
        String queryStr = (args != null) ? HttpUtil.encodeHttpArgs(args) : "";
        DnRequestHandler requestHandler = new DnRequestHandler(instanceConfig.instanceName,
                EPM_GET, endpoint, curHeaders, cookies);

        requestHandler.queryStr = queryStr;
        executeRequest(requestHandler);
        return requestHandler;
    }

    public DnRequestHandler sendEditRequest(String endpoint, Map<String,Object> args, Map<String,Object> data,
            boolean isPut) {
        String method = (isPut) ? EPM_PUT : EPM_POST;
        String queryStr = (args != null) ? HttpUtil.encodeHttpArgs(args) : "";
        String postData = data != null ? ParsingUtil.toJsonString(data) : "";
        DnRequestHandler requestHandler = new DnRequestHandler(instanceConfig.instanceName,
                method, endpoint, curHeaders, cookies);
        requestHandler.queryStr = queryStr;
        requestHandler.testPostData = postData;
        executeRequest(requestHandler);
        return requestHandler;
    }

    public void extractCookies(DnRequestHandler requestHandler) {
        // Add code to add cookies to *curHeaders*.
        List<String> cookieStrs = requestHandler.rptResponseHeaders.get("set-cookie");
        if (cookieStrs != null) {
            for (String cookieStr : cookieStrs) {
                String[] args = cookieStr.split(";");
                String[] nameValue = args[0].split("=");
                if (nameValue.length == 2) {
                    cookies.put(nameValue[0].trim(), nameValue[1].trim());
                }
            }
        }
    }

    public void executeRequest(DnRequestHandler requestHandler) {
        requestHandler.handleRequest();
        extractCookies(requestHandler);
    }


}
