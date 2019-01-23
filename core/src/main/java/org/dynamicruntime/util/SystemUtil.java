package org.dynamicruntime.util;

import org.dynamicruntime.exception.DnException;

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
}
