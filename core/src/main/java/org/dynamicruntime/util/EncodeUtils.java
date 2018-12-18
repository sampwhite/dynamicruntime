package org.dynamicruntime.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@SuppressWarnings({"WeakerAccess", "unused"})
public class EncodeUtils {
    public static String uuEncode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] uuDecode(String str) {
        return Base64.getDecoder().decode(str);
    }

    public static String bigHash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] result = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return uuEncode(result);
        } catch (Exception e) {
            throw new RuntimeException("Could not do MD5 hash", e);
        }
    }

    /** Makes a shorter string out of a long one. The idea is to give some idea of the original
     * contents of the string by putting some of it in the output and combining it with a hash
     * that is fairly guaranteed to create a unique result. The maxLen parameter should not
     * be less than 40. */
    public static String mkUniqueShorterStr(String text, int maxLen) {
        int l = text.length();
        if (l <= maxLen) {
            return text;
        }
        // Limit characters in hash to 20 (good enough for uniqueness).
        String h = bigHash(text).substring(0, 20);
        int leftover = maxLen - 20;
        int start = leftover/2;
        int end = leftover - start;
        return text.substring(0, start) + h + text.substring(l - end, l);

    }
}
