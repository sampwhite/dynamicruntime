package org.dynamicruntime.content;

import org.eclipse.jetty.http.MimeTypes;

import java.io.File;
import java.util.Date;

@SuppressWarnings("WeakerAccess")
public class DnContentData {
    public String mimeType;
    public boolean isBinary;
    public String strContent;
    public byte[] binaryContent;
    public boolean immutable;
    public Date timestamp;
    /** Optional field that indicates where this resource was retrieved, if it came from the file system. This
     * sometimes can be supplied as an alternative to *binaryContent*.*/
    public File fileLocation;

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
