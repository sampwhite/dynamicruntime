package org.dynamicruntime.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Date;

public class ConvertUtil {
    public static ThreadLocal<DecimalFormat> decimalFormatter =
            ThreadLocal.withInitial(() -> new DecimalFormat("0.0##"));
    /** Creates a report version of an object. Also used to create JSON representations of primitive objects. */
    public static String fmtObject(Object o) {
        if (o == null) {
            return "<null>";
        }
        if (o instanceof Date) {
            return DnDateUtil.formatDate((Date)o);
        }
        if (o instanceof Float || o instanceof Double || o instanceof BigDecimal) {
            return fmtDouble(((Number)o).doubleValue());
        }
        return o.toString();
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

}
