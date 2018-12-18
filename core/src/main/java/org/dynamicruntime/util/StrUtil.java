package org.dynamicruntime.util;

import java.util.List;

@SuppressWarnings("WeakerAccess")
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

    public static String getToNextIndex(String str, int offset, String sep) {
        if (str == null || offset >= str.length()) {
            return "";
        }
        int index = str.indexOf(sep, offset);
        if (index < 0) {
            return str.substring(offset);
        }
        return str.substring(offset, index);
    }

    /** Capitalizes a string, but it assumes the string is limited to a character set used for variable names. */
    public static String capitalize(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        char ch = str.charAt(0);
        if (ch >= 'a' && ch <= 'z') {
            char uCh = (char)('A' + (ch - 'a'));
            return uCh + str.substring(1);
        }
        return str;
    }

    /** Used to convert Java variable names to lower case column names in databases, such as postgres.
     * Turns MyColumnName into my_column_name. */
    public static String toLowerCaseIdentifier(String str) {
        var sb = new StringBuilder(str.length() + 4);
        boolean priorIsLower = false;
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if ((ch >= 'A' && ch <= 'Z')) {
                if (priorIsLower) {
                    sb.append('_');
                }
                sb.append((char)(ch - 'A' + 'a'));
                priorIsLower = false;
            } else {
                priorIsLower = true;
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static String turnPathIntoName(String path) {
        int index = 0;
        StringBuilder sb = new StringBuilder();
        while (index < path.length()) {
            int nextIndex = path.indexOf("/", index);
            if (nextIndex < 0) {
                sb.append(StrUtil.capitalize(path.substring(index)));
                break;
            }
            if (nextIndex > index) {
                sb.append(StrUtil.capitalize(path.substring(index, nextIndex)));
            }
            index = nextIndex + 1;
        }
        return sb.toString();
    }

    /** Encodes a string to be used in JSON output. Taken from JSON simple's JSONValue and altered. */
    public static void escapeLiteralString(StringBuilder sb, String str) {
        for(int i = 0; i < str.length(); i++){
            char ch = str.charAt(i);
            switch(ch){
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    //Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if((ch <= '\u001F') || (ch >= '\u007F' && ch <= '\u009F') || (ch >= '\u2000' && ch <= '\u20FF')){
                        String ss=Integer.toHexString(ch);
                        sb.append("\\u");
                        for(int k = 0; k < 4 - ss.length(); k++){
                            sb.append('0');
                        }
                        sb.append(ss.toUpperCase());
                    }
                    else{
                        sb.append(ch);
                    }
            }
        } // end for loop
    }

    public static List<String> splitString(String str, String sep) {
        String[] values = str.split(sep);
        return DnCollectionUtil.mListA(values);
    }

    public static String limitStringSize(String str, int maxLen) {
        if (str != null && str.length() > maxLen) {
            return str.substring(0, maxLen - 3) + "...";
        }
        return str;
    }
}
