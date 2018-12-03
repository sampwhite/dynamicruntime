package org.dynamicruntime.util;

import static org.dynamicruntime.util.DnCollectionUtil.*;
import static org.dynamicruntime.util.ConvertUtil.*;
import org.dynamicruntime.exception.DnException;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


@SuppressWarnings("WeakerAccess")
public class ParsingUtil {
    public static String INDENT_STRING = "    ";

    /** Parses a JSON map from a string. */
    @SuppressWarnings("unchecked")
    public static Map<String,Object> toJsonMap(String str) throws DnException {
        if (str == null || str.indexOf('{') < 0) {
            return mMap();
        }

        try {
            JSONParser parser = new JSONParser();
            Object o = parser.parse(str);
            if (o instanceof JSONObject) {
                var m = (JSONObject)o;
                return (Map<String,Object>)m;
            }
            throw DnException.mkConv("String holding JSON did not convert to a map.", null);
        } catch (ParseException pe) {
            throw DnException.mkConv("Unable to parse JSON map from string.", pe);
        }
    }

    /** Parsed a JSON list from a string. */
    /* Commented out until needed.
    @SuppressWarnings("unchecked")
    public static List<Object> toJsonList(String str) throws DnException {
        if (str == null || str.indexOf('[') < 0) {
            return mList();
        }

        try {
            JSONParser parser = new JSONParser();
            Object o = parser.parse(str);
            if (o instanceof org.json.simple.JSONArray) {
                var a = (org.json.simple.JSONArray)o;
                return (List<Object>)a;
            }
            throw DnException.mkConv("String holding JSON did not convert to a list.", null);
        } catch (ParseException pe) {
            throw DnException.mkConv("Unable to parse JSON list from string.", pe);
        }
    }
    */

    public static String toJsonString(Object obj) {
        return toJsonString(obj, false);
    }

    public static String toJsonString(Object obj, boolean isCompact) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, obj, 0, isCompact);
        return sb.toString();
    }

    public static void appendJson(StringBuilder sb, Object obj, int nestLevel, boolean isCompact) {
        if (obj == null) {
            sb.append("null");
            return;
        }
        if (obj instanceof Map) {
            if (nestLevel > 20) {
                sb.append("{}");
                return;
            }
            Map<?,?> m = (Map<?,?>)obj;
            String mapBegin = isCompact ? "{" : "{\n";
            sb.append(mapBegin);
            List<Map.Entry<?,?>> copy = mList();
            for (Map.Entry<?,?> entry : m.entrySet()) {
                var k = entry.getKey();
                if (k != null) {
                    var v = entry.getValue();
                    if (v != null) {
                        copy.add(entry);
                    }
                }
            }
            copy.sort((v1,v2) -> {
                Object vv1 = v1.getValue();
                Object vv2 = v2.getValue();
                boolean b1 = (vv1 instanceof Collection || vv1 instanceof Map);
                boolean b2 = (vv2 instanceof Collection || vv2 instanceof Map);
                if (b1 != b2) {
                    return b1 ? 1 : -11;
                }
                String k1 = v1.getKey().toString();
                String k2 = v2.getKey().toString();
                return k1.compareTo(k2);
            });

            boolean isFirst = true;
            for (Map.Entry<?,?> entry : copy) {
                Object k = entry.getKey();
                Object v = entry.getValue();
                if (!isFirst) {
                    String nextItemStr = isCompact ? "," : ",\n";
                    sb.append(nextItemStr);
                }
                String ks = k.toString();
                if (!isCompact) {
                    appendIndents(sb, nestLevel + 1);
                }
                sb.append("\"");
                StrUtil.escapeLiteralString(sb, ks);
                sb.append("\":");
                appendJson(sb, v, nestLevel + 1, isCompact);
                isFirst = false;
            }
            if (!isCompact && !isFirst) {
                sb.append('\n');
                appendIndents(sb, nestLevel);
            }
            sb.append("}");

        } else if (obj instanceof Collection) {
            if (nestLevel > 20) {
                sb.append("[]");
                return;
            }
            Collection c = (Collection)obj;
            sb.append("[");
            boolean isFirst = true;
            for (Object item : c) {
                if (!isFirst) {
                    sb.append(",");
                }
                appendJson(sb, item, nestLevel + 1, isCompact);
                isFirst = false;
            }
            sb.append(']');
        } else if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(fmtObject(obj));
        } else {
            String objStr = fmtObject(obj);
            sb.append('\"');
            StrUtil.escapeLiteralString(sb, objStr);
            sb.append('\"');
        }
    }

    public static void appendIndents(StringBuilder sb, int nestLevel) {
        for (int i = 0; i < nestLevel; i++) {
            sb.append(INDENT_STRING);
        }
    }


    public static boolean isJsonEqual(Object o1, Object o2) {
        return isJsonEqual(o1, o2, 0);
    }

    public static boolean isJsonEqual(Object o1, Object o2, int nestLevel) {
        if (o1 == null || o2 == null) {
            return o1 == null && o2 == null;
        }
        if (o1 instanceof Map) {
            if (nestLevel > 20) {
                return false;
            }
            if (!(o2 instanceof Map)) {
                return false;
            }
            Map<?,?> m1 = (Map<?,?>)o1;
            Map<?,?> m2 = (Map<?,?>)o2;
            int s1 = countNotNull(m1.values());
            int s2 = countNotNull(m2.values());

            if (s1 != s2) {
                return false;
            }
            for (Object key : m1.keySet()) {
                Object v1 = m1.get(key);
                Object v2 = m2.get(key);
                if (!isJsonEqual(v1, v2, nestLevel + 1)) {
                    return false;
                }
            }
            return true;
        } else if (o1 instanceof Collection) {
            if (nestLevel > 20) {
                return false;
            }
            if (!(o2 instanceof Collection)) {
                return false;
            }
            Collection<?> c1 = (Collection<?>)o1;
            Collection<?> c2 = (Collection<?>)o2;
            if (c1.size() != c2.size()) {
                return false;
            }
            var it1 = c1.iterator();
            var it2 = c2.iterator();

            while (it1.hasNext()) {
                var v1 = it1.next();
                var v2 = it2.next();
                if (!isJsonEqual(v1, v2, nestLevel + 1)) {
                    return false;
                }
            }
            return true;
        } else if (o1 instanceof Date && o2 instanceof Date) {
            return o1.equals(o2);
        } else if (o1 instanceof Number || o2 instanceof Number) {
            if (!(o1 instanceof Number) || !(o2 instanceof Number)) {
                return false;
            }
            return areCloseNumbers((Number)o1, (Number)o2);
        } else if (o1 instanceof Boolean) {
            return o1.equals(o2);
        }
        // Convert to string and then compare.
        String s1 = fmtObject(o1);
        String s2 = fmtObject(o2);
        return s1.equals(s2);
    }
}
