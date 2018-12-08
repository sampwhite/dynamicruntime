package org.dynamicruntime.content;

import org.eclipse.jetty.http.MimeTypes;

@SuppressWarnings("WeakerAccess")
public class DnContentData {
    public String mimeType;
    public boolean isBinary;
    public String strContent;
    public byte[] binaryContent;

    public DnContentData(String mimeType, boolean isBinary, String strContent, byte[] binaryContent) {
        this.mimeType = mimeType;
        this.isBinary = isBinary;
        this.strContent = strContent;
        this.binaryContent = binaryContent;
    }

    public static DnContentData mkHtml(String html) {
        return new DnContentData("text/html", false, html, null);
    }
 }
