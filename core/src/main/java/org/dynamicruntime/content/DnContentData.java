package org.dynamicruntime.content;

import org.eclipse.jetty.http.MimeTypes;

import java.util.Date;

@SuppressWarnings("WeakerAccess")
public class DnContentData {
    public String mimeType;
    public boolean isBinary;
    public String strContent;
    public byte[] binaryContent;
    public int cacheMaxAge;
    // Maybe someday used for *Etag* headers. Also may be used for an internal in-memory cache, especially if
    // conversions are being performed on content (such as transforming images).
    @SuppressWarnings("unused")
    public Date timestamp;

    public DnContentData(String mimeType, boolean isBinary, String strContent, byte[] binaryContent, Date timestamp) {
        this.mimeType = mimeType;
        this.isBinary = isBinary;
        this.strContent = strContent;
        this.binaryContent = binaryContent;
    }

    public static DnContentData mkHtml(String html) {
        return new DnContentData("text/html", false, html, null, null);
    }
 }
