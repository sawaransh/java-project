package edu.hostel.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 120_000;
    private PasswordUtil() {}

    public static String newSalt() {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt); return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String password, String salt) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, Base64.getDecoder().decode(salt), ITERATIONS, 256);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (Exception e) { throw new IllegalStateException("Password hashing is unavailable", e); }
        finally { spec.clearPassword(); java.util.Arrays.fill(chars, '\0'); }
    }

    public static boolean matches(String candidate, String salt, String expectedHash) {
        byte[] actual = Base64.getDecoder().decode(hash(candidate, salt));
        byte[] expected = Base64.getDecoder().decode(expectedHash);
        return MessageDigest.isEqual(actual, expected);
    }
}
