package org.dynamicruntime.util;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

import java.io.Closeable;

@SuppressWarnings("WeakerAccess")
public class SystemUtil {
    public static void sleep(int duration) throws DnException {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            throw new DnException(String.format("Interrupted during sleep of %d", duration), e,
                    DnException.INTERNAL_ERROR, DnException.SYSTEM, DnException.INTERRUPTED);
        }
    }

    public static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (Exception ignore) {}
    }

    /** Create thread name that is different per instance that is running. */
    public static String createThreadName(DnCxt cxt, String threadName) {
        String instanceName = (cxt != null) ? cxt.instanceConfig.instanceName : "NoInstance";
        return instanceName + "-" + threadName;
    }
}
