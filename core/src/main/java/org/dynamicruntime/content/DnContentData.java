package org.dynamicruntime.content;

import org.eclipse.jetty.http.MimeTypes;

import java.util.Date;

@SuppressWarnings("WeakerAccess")
public class DnContentData {
    public String mimeType;
    public boolean isBinary;
    public String strContent;
    public byte[] binaryContent;
    public boolean immutable;
    public Date timestamp;

    public DnContentData(String mimeType, boolean isBinary, String strContent, byte[] binaryContent, Date timestamp) {
        this.mimeType = mimeType;
        this.isBinary = isBinary;
        this.strContent = strContent;
        this.binaryContent = binaryContent;
        this.timestamp = timestamp;
    }

    public static DnContentData mkHtml(String html) {
        return new DnContentData("text/html; charset=utf-8", false, html, null, null);
    }
 }
