package org.dynamicruntime.context;

import org.dynamicruntime.exception.DnException;

import java.util.Map;

import static org.dynamicruntime.util.ConvertUtil.*;

/** Convenience class for accessing configuration values. */
@SuppressWarnings({"WeakerAccess", "unused"})
public class DnConfigUtil {
    public static Object getConfigObject(DnCxt cxt, String key, Object dflt, String description,
            Map<String,Object> extras) {
        Object obj = cxt.instanceConfig.get(key);
        if (obj == null) {
            obj = dflt;
        }
        if (obj != null || extras != null) {
            DnConfigReport rpt = new DnConfigReport(key, obj, description, dflt, extras);
            cxt.instanceConfig.configAccessReport.put(key, rpt);
        }
        return obj;
    }

    public static String getConfigString(DnCxt cxt, String key, String dflt, String description,
            Map<String,Object> extras) {
        Object obj = getConfigObject(cxt, key, dflt, description, extras);
        return toOptStr(obj);
    }

    public static String getConfigString(DnCxt cxt, String key, String dflt, String description) {
        return getConfigString(cxt, key, dflt, description, null);
    }

    public static String getReqConfigString(DnCxt cxt, String key, String description) throws DnException {
        String s = getConfigString(cxt, key, null, description);
        throwExceptionIfNull(s, key);
        return s;
    }

    public static Boolean getConfigBool(DnCxt cxt, String key, String description) {
        Object obj = getConfigObject(cxt, key, null, description, null);
        return toOptBool(obj);
    }

    public static boolean getConfigBool(DnCxt cxt, String key, boolean dflt, String description) {
        Object obj =  getConfigObject(cxt, key, dflt, description, null);
        return toBoolWithDefault(obj, dflt);
    }

    public static Long getConfigLong(DnCxt cxt, String key, String description) throws DnException {
        Object obj = getConfigObject(cxt, key, null, description, null);
        return toOptLong(obj);
    }

    public static long getConfigLong(DnCxt cxt, String key, long dflt, String description) throws DnException {
        Object obj =  getConfigObject(cxt, key, dflt, description, null);
        return toLongWithDefault(obj, dflt);
    }

    public static long getReqConfigLong(DnCxt cxt, String key, String description) throws DnException {
        Long l = getConfigLong(cxt, key, description);
        throwExceptionIfNull(l, key);
        return l;
    }

    public static void throwExceptionIfNull(Object obj, String key) throws DnException {
        if (obj == null) {
            throw new DnException(String.format("Cannot find a configuration value for %s.", key), null,
                    DnException.NOT_FOUND, DnException.CONFIG, DnException.CODE);
        }
    }

    public static String getReqPrivateStr(DnCxt cxt, String key) throws DnException {
        String str = toOptStr(cxt.instanceConfig.get(key));
        throwExceptionIfNull(str, key);
        return str;
    }
}
