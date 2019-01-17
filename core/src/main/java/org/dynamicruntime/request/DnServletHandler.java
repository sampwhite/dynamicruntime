package org.dynamicruntime.request;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface DnServletHandler {
    boolean hasResponseBeenSent();
    void setResponseHasBeenSent(boolean beenSent);
    String getTarget();
    // Let auth implementation get cookies.
    List<String> getRequestHeader(String header);
    // Let auth implementation set cookies.
    void addResponseHeader(String header, String value);
    void sendRedirect(String redirectUrl) throws IOException;
}
