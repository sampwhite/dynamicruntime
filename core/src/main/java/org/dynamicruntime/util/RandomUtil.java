package org.dynamicruntime.util;

import java.security.SecureRandom;
import java.util.Random;

@SuppressWarnings("WeakerAccess")
public class RandomUtil {
    public static volatile SecureRandom secureRandom = null;
    public static final ThreadLocal<Random> random = ThreadLocal.withInitial(() ->
            new Random(getSecureRandom().nextLong()));

    public static SecureRandom getSecureRandom() {
        // We do not construct secure random during class initialization because it may take some time.
        if (secureRandom == null) {
            // Do not really care if we end up creating more than one of these due to race condition.
            secureRandom = new SecureRandom();
        }
        return secureRandom;
    }

    public static Random getRandom() {
        return random.get();
    }
}
