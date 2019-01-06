package org.dynamicruntime.util;

public class PageUtil {
    public static String extractSection(String text, String beginTag, String endTag) {
        int index = text.indexOf(beginTag);
        if (index < 0) {
            return null;
        }
        int start = index + beginTag.length();
        int end = text.indexOf(endTag, start);
        if (end < 0) {
            return null;
        }
        return text.substring(start, end);
    }
}
