package org.dynamicruntime.content;

import java.net.FileNameMap;
import java.net.URLConnection;

@SuppressWarnings("WeakerAccess")
public class DnContentUtil {
    public static final FileNameMap fileNameMap = URLConnection.getFileNameMap();

    public static String determineMimeType(String webPath) {
        String mimeType = fileNameMap.getContentTypeFor(webPath);
        // For some reasons, the default mime type is missing some common extensions.
        if (mimeType == null && webPath.endsWith(".ico")) {
            mimeType = "image/ico";
        }
        if (mimeType == null && webPath.endsWith(".js")) {
            mimeType = "application/javascript";
        }
        if (mimeType == null && webPath.endsWith(".json")) {
            mimeType = "application/json";
        }
        if (mimeType == null && webPath.endsWith(".css")) {
            mimeType = "text/css";
        }
        if (mimeType == null && webPath.endsWith(".yaml")) {
            mimeType = "text/yaml";
        }
        if (mimeType == null && webPath.endsWith(".map")) {
            mimeType = "application/json map";
        }
        if (mimeType == null) {
            mimeType = "text/plain";
        }

        if (mimeType.contains("text")) {
            mimeType = mimeType + "; charset=utf-8";
        }
        return mimeType;
    }

    public static boolean isBinary(String mimeType) {
        return mimeType.startsWith("image") || mimeType.startsWith("video") ||
                mimeType.startsWith("audio") || mimeType.contains("octet") || mimeType.contains("zip");
    }

}
