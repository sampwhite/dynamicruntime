package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

@SuppressWarnings("WeakerAccess")
public class ConvertUtil {
    public static final ThreadLocal<DecimalFormat> decimalFormatter =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.0##"));
    /** Creates a report version of an object. Also used to create JSON representations of primitive objects. */
    public static String fmtObject(Object o) {
        if (o instanceof CharSequence || o instanceof Integer || o instanceof Long) {
            return o.toString();
        }
        StringBuilder sb = new StringBuilder();
        fmtObject(o, sb, 0);
        return sb.toString();
    }

    public static void fmtObject(Object o, StringBuilder sb, int nestLevel) {
       if (o == null) {
            sb.append("<null>");
            return;
        }
        if (o instanceof Date) {
            sb.append(DnDateUtil.formatDate((Date)o));
        }
        else if (o instanceof Float || o instanceof Double || o instanceof BigDecimal) {
            sb.append(fmtDouble(((Number)o).doubleValue()));
        } else if (o instanceof Collection) {
            if (nestLevel > 2) {
                // Only format to so much depth.
                sb.append("[...]");
                return;
            }
            var c = (Collection)o;
            if (c.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[");
            StringBuilder cSb = new StringBuilder(); // Re-usable string builder.
            boolean isFirst = true;
            for (Object cObj :c) {
                if (!isFirst) {
                     sb.append(',');
                }
                appendFmtString(sb, cSb, cObj, nestLevel + 1);
                isFirst = false;
            }
            sb.append(']');
        } else if (o instanceof Map) {
            if (nestLevel > 2) {
                // Only format to so much depth.
                sb.append("[.:.]");
                return;
            }
            Map m = (Map)o;
            if (m.isEmpty()) {
                sb.append("[:]");
                return;
            }
            StringBuilder mSb = new StringBuilder(); // Re-usable string builder.
            sb.append('[');
            boolean isFirst = true;
            for (Object key : m.keySet()) {
                if (!isFirst) {
                    sb.append(',');
                }
                Object val = m.get(key);
                appendFmtString(sb, mSb, key, nestLevel + 1);
                sb.append(':');
                appendFmtString(sb, mSb, val, nestLevel + 1);
                isFirst = false;
            }
            sb.append(']');
        }
        else {
            StrUtil.escapeLiteralString(sb, o.toString());
        }
    }

    static void appendFmtString(StringBuilder sb, StringBuilder tempSb, Object o, int nestLevel) {
        fmtObject(o, tempSb, nestLevel);
        if (o instanceof Collection || o instanceof Map) {
            sb.append(tempSb);
        } else {
            String s = tempSb.toString();
            boolean hasCommasOrBackslashes = s.indexOf(',') >= 0 || s.indexOf('\\') >= 0;
            if (hasCommasOrBackslashes) {
                sb.append("\"");
            }
            sb.append(s);
            if  (hasCommasOrBackslashes) {
                sb.append("\"");
            }
        }
        // *The *tempSb* is a reusable parameter.
        tempSb.setLength(0);
    }

    public static String fmtDouble(double d) {
        return decimalFormatter.get().format(d);
    }

    public static boolean areCloseNumbers(Number n1, Number n2) {
        if (n1 == null || n2 == null) {
            return n1 == null && n2 == null;
        }
        if ((n1 instanceof Float || n1 instanceof Double || n1 instanceof BigDecimal) ||
                (n2 instanceof Float || n2 instanceof Double || n2 instanceof BigDecimal)) {
            var d1 = n1.doubleValue();
            var d2 = n2.doubleValue();
            var diff = d1 - d2;
            return (diff < 0.001 && diff > -0.001);
        }
        // Note, the *equals* method treats Longs as being different from Integers, so we have
        // to do this the hard way.
        long l1 = n1.longValue();
        long l2 = n2.longValue();
        return l1 == l2;
    }

    /** Converts to string and returns null is string is an empty string. Follows database
     * convention that empty string and null string are the same. */
    public static String toOptStr(Object o) {
        if (o instanceof CharSequence) {
            String s = o.toString();
            return s.isEmpty() ? null : s;
        }
        return null;
    }

    public static String toReqStr(Object o) throws DnException {
        String s = toOptStr(o);
        if (s == null) {
            throw DnException.mkConv(String.format("Could not coerce '%s' to required string", o), null);
        }
        return s;
    }

    public static String getOptStr(Map<String,Object> map, String key) {
        if (map == null) {
            return null;
        }
        return toOptStr(map.get(key));
    }

    public static String getReqStr(Map<String,Object> map, String key) throws DnException {
        if (map == null) {
            throw DnException.mkConv(
                    String.format("Could not get '%s' from null map.", key), null);
        }
        String s = getOptStr(map, key);
        if (s == null) {
            throw DnException.mkConv(String.format("Value for '%s' in data " +
                    "was not present, empty, or not a string.", key), null);

        }
        return s;
    }

    public static boolean isEmpty(Object o) {
        if (o == null) {
            return true;
        }
        if (o instanceof CharSequence) {
            return ((CharSequence)o).length() == 0;
        } else if (o instanceof Collection) {
            return ((Collection)o).isEmpty();
        } else if (o instanceof Map) {
            return ((Map)o).isEmpty();
        }
        return false;
    }

    public static Long toOptLong(Object o) throws DnException {
        Long retVal = null;
        if (o instanceof Number) {
            retVal = coerceNumToLong((Number)o);
        } else if (o instanceof CharSequence) {
            String s = o.toString();
            if (s.isEmpty()) {
                return null;
            }
            try {
                retVal = Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw DnException.mkConv("Failed to convert " + o + " to a long.", e);
            }
        }
        return retVal;
    }

    public static long toReqLong(Object o) throws DnException {
        Long l = toOptLong(o);
        if (l == null) {
            throw DnException.mkConv(String.format("Could not convert '%s' to a long value", o), null);
        }
        return l;
    }

    public static long coerceNumToLong(@NotNull Number num) {
        if (num instanceof Float || num instanceof Double ||
            num instanceof BigDecimal) {
            double d = num.doubleValue();
            if (d < 0) {
                d -= 0.499;
            } else {
                d += 0.499;
            }
            return (long)d;
        }
        return num.longValue();
    }

    public static long toLongWithDefault(Object o, long dflt) throws DnException {
        Long l = toOptLong(o);
        return l != null ? l : dflt;
    }

    public static Long getOptLong(Map<String,Object> map, String key) throws DnException {
        if (map == null) {
            return null;
        }
        Object o = map.get(key);
        try {
            return toOptLong(o);
        } catch (DnException e) {
            throw DnException.mkConv(String.format("Could not extract long value using key '%s'.", key), e);
        }
    }

    public static Long getReqLong(Map<String,Object> map, String key) throws DnException {
        if (map == null) {
            return null;
        }
        Object o = map.get(key);
        try {
            return toReqLong(o);
        } catch (DnException e) {
            throw DnException.mkConv(String.format("Could not extract long value using key '%s'.", key), e);
        }
    }

    public static long getLongWithDefault(Map<String,Object> map, String key, long dflt) throws DnException {
        Long l = getOptLong(map, key);
        return l != null ? l : dflt;
    }

    public static Boolean toOptBool(Object o) {
        if (o instanceof Boolean) {
            return (Boolean)o;
        } else if (o instanceof Number) {
            return coerceNumToLong((Number)o) != 0;
        } else if (o instanceof CharSequence) {
            var cs = (CharSequence)o;
            if (cs.length() == 0) {
                return null;
            }
            var firstChar = cs.charAt(0);
            if (firstChar == '1' || firstChar == 't' || firstChar == 'T' || firstChar == 'y' || firstChar == 'Y') {
                return Boolean.TRUE;
            }
            if (firstChar == '0' || firstChar == 'f' || firstChar == 'F' || firstChar == 'n' || firstChar == 'N') {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    public static boolean toBoolWithDefault(Object o, boolean dflt) {
        Boolean b = toOptBool(o);
        return b != null ? b : dflt;
    }

    public static Boolean getOptBool(Map<String,Object> map, String key) {
        if (map == null) {
            return null;
        }
        return toOptBool(map.get(key));
    }

    public static boolean getBoolWithDefault(Map<String,Object> map, String key, boolean dflt) {
        if (map == null) {
            return dflt;
        }
        return toBoolWithDefault(map.get(key), dflt);
    }

    public static Date toOptDate(Object o) throws DnException {
        if (o instanceof Date) {
            return (Date)o;
        } else if (o instanceof CharSequence) {
            String s = o.toString();
            if (s.isEmpty()) {
                return null;
            }
            return DnDateUtil.parseDate(s);
        }
        return null;
    }

    public static Date toReqDate(Object o) throws DnException {
        Date d = toOptDate(o);
        if (d == null) {
            throw DnException.mkConv(String.format("Object '%s' could not be coerced to date.", o), null);
        }
        return d;
    }

    public static Date getOptDate(Map<String,Object> map, String key) throws DnException {
        if (map == null) {
            return null;
        }
        try {
            return toOptDate(map.get(key));
        } catch (DnException e) {
            throw DnException.mkConv(String.format("Could not extract date value using key '%s'.", key), e);
        }
    }

    public static Date getReqDate(Map<String,Object> map, String key) throws DnException {
        Date d;
        try {
            d = getOptDate(map, key);
        } catch (DnException e) {
            throw DnException.mkConv(String.format("Could not extract date value using key '%s'.", key), e);
        }
        if (d == null) {
            throw DnException.mkConv(String.format("There was no date value at key '%s'.", key), null);
        }
        return d;
    }

    @SuppressWarnings("unchecked")
    public static Map<String,Object> toOptMap(Object o) {
        if (o instanceof Map) {
            return (Map<String,Object>)o;
        }
        return null;
    }

    public static Map<String,Object> getOptMap(Map<String,Object> map, String key) {
        return map != null ? toOptMap(map.get(key)) : null;
    }

    /** Gets a map object out of a map. However, if this method returns an empty map, then the
     * returned empty map may not have been stored in the containing map, so modifying it will not modify the
     * containing map's interior attributes.
     */
    public static Map<String,Object> getMapDefaultEmpty(Map<String,Object> map, String key) {
        Map<String,Object> retVal = null;
        if (map != null) {
            retVal = toOptMap(map.get(key));
        }
        return (retVal != null) ? retVal : new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String,Object>> toOptListOfMaps(Object o) {
        if (!(o instanceof List)) {
            return null;
        }
        List l = (List)o;
        for (var entry : l) {
            if (!(entry instanceof Map)) {
                return null;
            }
        }
        // We are going to assume that lists of maps have all string keys, its gets expensive on the CPU
        // to verify this.
        return (List<Map<String,Object>>)l;
    }

    public static List<Map<String,Object>> getOptListOfMaps(Map<String,Object> map, String key) {
        return map != null ? toOptListOfMaps(map.get(key)) : null;
    }

    public static List<Map<String,Object>> getListOfMapsDefaultEmpty(Map<String,Object> map , String key) {
        List<Map<String,Object>> retVal = null;
        if (map != null) {
            retVal = toOptListOfMaps(map.get(key));
        }
        return (retVal != null) ? retVal : new ArrayList<>();
    }
}
