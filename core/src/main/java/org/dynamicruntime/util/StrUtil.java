package org.dynamicruntime.util;

import javax.lang.model.SourceVersion;
import java.util.List;
import static org.dynamicruntime.util.DnCollectionUtil.*;

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
        return splitString(str, sep, -1);
    }

    /** Does a split, but without using a regular expression. If maxSplit is greater or equal to
     * zero, then it is the maximum size of the list that will be returned. The splitting
     * starts from the beginning of the string. The return value is a mutable list. If a separator
     * occurs at the end of the string, then the returned list has an empty string at the end
     * (assuming *maxSplit* has not already been reached). */
    public static List<String> splitString(String str, String sep, int maxSplit) {
        if (maxSplit < 0) {
            maxSplit = 1000000;
        }
        List<String> results = mList();
        if (maxSplit == 0) {
            return mList();
        }
        int w = sep.length();
        int index = 0;
        while (true) {
            int nextIndex = str.indexOf(sep, index);
            if (nextIndex < 0 || results.size() >= maxSplit - 1) {
                // End it.
                results.add(str.substring(index));
                break;
            }
            results.add(str.substring(index, nextIndex));
            index = nextIndex + w;
        }
        return results;
    }

    public static String limitStringSize(String str, int maxLen) {
        if (str != null && str.length() > maxLen) {
            return str.substring(0, maxLen - 3) + "...";
        }
        return str;
    }

    /**
     * Verifies that entry would be a valid name of a Java variable. This
     * is a pass thru function to the internal Java call that validates whether a string
     * would be a valid variable name.
     *
     * Side note: The more strings that are persistently stored that can be known to pass this
     * validation, the friendlier the application is to scripting, external query constructions,
     * full text search, and CSV file outputs. Generally any names of entities that are used as
     * the primary key for the entity should pass this test, unless there is a good reason otherwise (email
     * address or domain name is are examples where you cannot use this test).
     */
    public static boolean isJavaName(String str) {
        return SourceVersion.isName(str);
    }
}
