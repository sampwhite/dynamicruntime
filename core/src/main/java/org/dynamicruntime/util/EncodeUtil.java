package org.dynamicruntime.util;

import org.dynamicruntime.context.DnCxt;
import org.dynamicruntime.exception.DnException;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Date;

@SuppressWarnings({"WeakerAccess", "unused"})
public class EncodeUtil {
    public static final int NUM_HASH_BITS = 128;
    public static final int NUM_HASH_ITERATORS = 100000;
    public static final int NUM_RANDOM_ENCRYPT_BYTES = 12;
    public static final String PASSWORD_ENCODE_ALG = "pbkdf2";
    public static final String PASSWORD_ENCODE_ALG_PARAM = "PBKDF2WithHmacSHA512";

    public static final String ENCRYPTION_SIG = "AGN";
    public static final String KEY_SIG = "UU";

    public static final ThreadLocal<SecretKeyFactory> threadSecretKeyFactories =
            ThreadLocal.withInitial(EncodeUtil::mkSecretKeyFactory);
    public static final ThreadLocal<Cipher> threadCiphers =
            ThreadLocal.withInitial(EncodeUtil::mkCipher);


    public static String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] base64Decode(String str) {
        return Base64.getDecoder().decode(str);
    }

    public static String stdHash(String text) {
        return base64Encode(stdHashToBytes(text));
    }

    public static byte[] stdHashToBytes(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] result = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return result;
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
        String h = stdHash(text).substring(0, 20);
        int leftover = maxLen - 20;
        int start = leftover/2;
        int end = leftover - start;
        return text.substring(0, start) + h + text.substring(l - end, l);
    }

    public static String mkRndString(int numBytes) {
        var rnd = RandomUtil.getRandom();
        byte[] b = new byte[numBytes];
        rnd.nextBytes(b);
        return base64Encode(b);
    }

    public static String convertToReadableChars(byte[] bytes, int numBytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (int i = 0; i < numBytes; i++) {
            byte b = bytes[i];
            convertToReadableChars(b, sb);
        }
        return sb.toString();
    }

    // Characters are chosen so that even seen with poor vision, they are likely to be discerned correctly.
    public static char[] UNIQUE_LOOKING_CHARS = {'A', 'F', 'H', 'K', 'M', 'P', 'T', 'X',
                                                 'Y', 'W', 'Z', '3', '4', '6', '8', '9'};
    public static void convertToReadableChars(byte bVal, StringBuilder sb) {
        int b1 = (bVal >>> 4) & 0x0F;
        int b2 = bVal & 0x0F;
        sb.append(UNIQUE_LOOKING_CHARS[b1]);
        sb.append(UNIQUE_LOOKING_CHARS[b2]);
    }

    /** Creates this project's standard unique ID, usable for primary keys or globally unique values. We use
     * this function in preference to standard GUIDs because it can be sorted by timestamp, a useful
     * thing for troubleshooting and paging through result sets. This approach is used by Mongo for its
     * automatically generated primary keys, and it works well. */
    public static String mkUniqueId(DnCxt cxt) {
        Date d = cxt.now();
        // Returns a 36 character string, a bit long, but friendly in debug output and easy for code
        // to parse out the date portion.
        return DnDateUtil.formatDate(d) + mkRndString(8);
    }

    public static Date parseDateFromUniqueId(String uniqueId) throws DnException {
        int index = uniqueId.indexOf('Z');
        if (index < 0) {
            throw new DnException(String.format("Unique Id %s does not have a date encoded in it.", uniqueId));
        }
        String dateStr = uniqueId.substring(0, index);
        return DnDateUtil.parseDate(dateStr);
    }

    public static SecretKeyFactory mkSecretKeyFactory() {
        try {
            return SecretKeyFactory.getInstance( PASSWORD_ENCODE_ALG_PARAM );
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm " + PASSWORD_ENCODE_ALG + " is not supported", e);
        }
    }

    /** Implementation following recommendations of https://crackstation.net/hashing-security.htm */
    public static String hashPassword(String password) {
        SecureRandom sr = RandomUtil.getSecureRandom();
        byte[] salt = new byte[NUM_HASH_BITS/8];
        sr.nextBytes(salt);
        return hashPassword(salt, password);
    }

    /** Hashes a password based on security recommendations. In full production systems, this code
     * should probably be executed on node dedicated to authentication since it is deliberately CPU intensive.
     * The choice of *NUM_HASH_ITERATIONS* is designed to make the code take about
     * (very approximately since different CPUs can make a huge difference) 100 milliseconds to execute. */
    public static String hashPassword(byte[] salt, String password) {
        SecretKeyFactory keyFactory = threadSecretKeyFactories.get();
        char[] passwdChars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwdChars, salt, NUM_HASH_ITERATORS, NUM_HASH_BITS);
        try {
            SecretKey key = keyFactory.generateSecret(spec);
            byte[] hash = key.getEncoded();
            String saltStr = base64Encode(salt);
            String hashStr = base64Encode(hash);
            // Use pipe separator because it is not a commonly used separator.
            return PASSWORD_ENCODE_ALG + "|" + saltStr + "|" + hashStr;
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Could not generate hash", e);
        }
    }

    /** Verifies a password against a hashed version of the password. */
    public static boolean checkPassword(String password, String storedHash) throws DnException {
        // Escape pipe because split takes a regular expression.
        String[] parts = storedHash.split("\\|");
        if (parts.length != 3) {
            // Allow simple scenario of open password for test and developer systems, or systems
            // going through a bootstrap.
            return password.equals(storedHash);
        }
        String alg = parts[0];
        // Currently only supported one algorithm.
        if (!alg.equals(PASSWORD_ENCODE_ALG)) {
            throw DnException.mkConv(String.format("Algorithm %s for passwords is not supported.", alg));
        }
        byte[] salt = base64Decode(parts[1]);
        String predictedHash = hashPassword(salt, password);
        boolean result = predictedHash.equals(storedHash);
        // Randomize a little more the amount of time taken to execute this method, though given the way
        // Java works, a sleep of zero would probably create sufficient unpredictability.
        int sleepTime = RandomUtil.getRandom().nextInt(10);
        SystemUtil.sleep(sleepTime);
        return result;
    }

    public static Cipher mkCipher() {
        try {
            return Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Could not create cipher because of algorithm issue.", e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException("Could not create cipher because of a padding issue.", e);
        }
    }

    public static Cipher getCipher(int mode, SecretKey secretKey, AlgorithmParameterSpec parameterSpec) {
        Cipher cipher = threadCiphers.get();
        try {
            cipher.init(mode, secretKey, parameterSpec);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Could not init cipher because of key.", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Could not init cipher because of algorithm parameter.", e);
        }
        return cipher;
    }

    public static String encodeKey(byte[] keyBytes) {
        String s = base64Encode(keyBytes);
        return KEY_SIG + s;
    }

    public static String mkEncryptionKey() {
        SecureRandom sr = RandomUtil.getSecureRandom();
        byte[] keyBytes = new byte[16];
        sr.nextBytes(keyBytes);
        return encodeKey(keyBytes);
    }

    /** Encrypt plainText. The *key* is assumed to manufactured by encodeKey.
     * Following example laid out in
     * https://proandroiddev.com/security-best-practices-symmetric-encryption-with-aes-in-java-7616beaaade9 */
    public static String encrypt(String key, String plainText) throws DnException {
        SecureRandom sr = RandomUtil.getSecureRandom();
        byte[] iv = new byte[NUM_RANDOM_ENCRYPT_BYTES];
        sr.nextBytes(iv);
        byte[] keyBytes = getKeyBytes(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        byte[] textBytes = plainText.getBytes(StandardCharsets.UTF_8);

        Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        try {
            byte[] encodedBytes = cipher.doFinal(textBytes);
            ByteBuffer byteBuffer = ByteBuffer.allocate(NUM_RANDOM_ENCRYPT_BYTES + encodedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encodedBytes);
            byte[] cipherMessage = byteBuffer.array();
            return ENCRYPTION_SIG + base64Encode(cipherMessage);
        } catch (IllegalBlockSizeException e) {
            throw DnException.mkConv("Could not encrypt bytes because of block size issue.", e);
        } catch (BadPaddingException e) {
            throw DnException.mkConv("Could not encrypt bytes because of padding issue.", e);
        }
    }

    public static byte[] getKeyBytes(String key) throws DnException {
        if (!key.startsWith(KEY_SIG)) {
            throw new DnException("Encryption key is not usable for doing encryption.");
        }
        byte[] keyBytes = new byte[16];
        byte[] ourBytes = base64Decode(key.substring(KEY_SIG.length()));
        int l = ourBytes.length;
        if (l > 16) {
            l = 16;
        }
        System.arraycopy(ourBytes, 0, keyBytes, 0, l);
        return keyBytes;
    }

    public static String decrypt(String key, String encryptedText) throws DnException {
        if (!encryptedText.startsWith(ENCRYPTION_SIG)) {
            throw DnException.mkConv("Encrypted text does not start with proper signature.");
        }
        byte[] keyBytes = getKeyBytes(key);
        byte[] encryptedBytes = base64Decode(encryptedText.substring(ENCRYPTION_SIG.length()));
        ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
        byte[] iv = new byte[NUM_RANDOM_ENCRYPT_BYTES];
        byteBuffer.get(iv);
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        Cipher cipher = getCipher(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        try {
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (IllegalBlockSizeException e) {
            throw DnException.mkConv("Could not decrypt bytes because of block size issue.", e);
        } catch (BadPaddingException e) {
            throw DnException.mkConv("Could not decrypt bytes because of padding issue.", e);
        }
    }
}
