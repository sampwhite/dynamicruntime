package org.dynamicruntime.util;

public class StrUtil {
    public static String getBeforeLastIndex(String str, String sep) {
        int index = str.lastIndexOf(sep);
        if (index < 0) {
            return null;
        }
        return str.substring(0, index);
    }

    public static String getAfterLastIndex(String str, String sep) {
        int index = str.lastIndexOf(sep);
        if (index < 0) {
            return null;
        }
        return str.substring(index + sep.length());
    }

    /** Capitalizes a string, but it assumes the string can be used as the name of a variable. */
    public static String capitalize(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        char ch = str.charAt(0);
        if (ch >= 'a' && ch <= 'z') {
            char uCh = (char)('A' + (ch - 'a'));
            return Character.toString(uCh) + str.substring(1);
        }
        return str;
    }
}
