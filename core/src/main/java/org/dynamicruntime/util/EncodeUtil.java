package org.dynamicruntime.util;

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

@SuppressWarnings({"WeakerAccess", "unused"})
public class EncodeUtil {
    public static final int NUM_HASH_BITS = 128;
    public static final int NUM_HASH_ITERATORS = 100000;
    public static final String PASSWORD_ENCODE_ALG = "pbkdf2";
    public static final String PASSWORD_ENCODE_ALG_PARAM = "PBKDF2WithHmacSHA512";

    public static final String ENCRYPTION_SIG = "AGN";
    public static final String KEY_SIG = "UU";

    public static final ThreadLocal<SecretKeyFactory> threadSecretKeyFactories =
            ThreadLocal.withInitial(EncodeUtil::mkSecretKeyFactory);
    public static final ThreadLocal<Cipher> threadCiphers =
            ThreadLocal.withInitial(EncodeUtil::mkCipher);


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
            String saltStr = uuEncode(salt);
            String hashStr = uuEncode(hash);
            // Use pipe separator because it is not one of the characters produced by uuEncode.
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
        byte[] salt = uuDecode(parts[1]);
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
        String s = uuEncode(keyBytes);
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
        byte[] iv = new byte[12];
        sr.nextBytes(iv);
        byte[] keyBytes = getKeyBytes(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        byte[] textBytes = plainText.getBytes(StandardCharsets.UTF_8);

        Cipher cipher = getCipher(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        try {
            byte[] encodedBytes = cipher.doFinal(textBytes);
            ByteBuffer byteBuffer = ByteBuffer.allocate(4 + iv.length + encodedBytes.length);
            byteBuffer.putInt(iv.length);
            byteBuffer.put(iv);
            byteBuffer.put(encodedBytes);
            byte[] cipherMessage = byteBuffer.array();
            return ENCRYPTION_SIG + uuEncode(cipherMessage);
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
        byte[] ourBytes = uuDecode(key.substring(KEY_SIG.length()));
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
        byte[] encryptedBytes = uuDecode(encryptedText.substring(ENCRYPTION_SIG.length()));
        ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
        int ivLength = byteBuffer.getInt();
        if(ivLength < 12 || ivLength >= 16) { // check input parameter
            throw new IllegalArgumentException("Invalid iv length");
        }
        byte[] iv = new byte[ivLength];
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
