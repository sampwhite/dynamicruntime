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
                        for(int k=0;k<4-ss.length();k++){
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
}
