package org.dynamicruntime.request;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface DnServletHandler {
    boolean hasResponseBeenSent();
    void setResponseHasBeenSent(boolean beenSent);
    String getTarget();
    // All the objects should be strings.
    Map<String,Object> getQueryParams();
    Map<String,Object> getPostData();
    // Let auth code get cookies.
    List<String> getRequestHeader(String header);
    // Let auth code set cookies.
    void addResponseHeader(String header, String value);
    void sendRedirect(String redirectUrl) throws IOException;
}
