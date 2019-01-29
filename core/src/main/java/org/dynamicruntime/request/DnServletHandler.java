package org.dynamicruntime.request;

import org.dynamicruntime.user.UserAuthData;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface DnServletHandler {
    boolean hasResponseBeenSent();
    void setResponseHasBeenSent(boolean beenSent);
    String getTarget();
    // Let auth implementation get cookies.
    List<String> getHeaderNames();
    String getRequestHeader(String header);
    List<String> getRequestHeaders(String header);
    // Let auth implementation set cookies.
    void addResponseHeader(String header, String value);
    UserAuthData getUserAuthData();
    void setUserAuthData(UserAuthData userAuthData);
    void setAuthCookieOnResponse(boolean setAuthCookie);
    void setIsLogout(boolean isLogout);
    Map<String,String> getRequestCookies();
    void addResponseCookie(String cookieName, String cookieValue, Date expireDate);
    void sendRedirect(String redirectUrl) throws IOException;
}
