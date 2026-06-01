package org.akuunda.akuundawallet.common.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public final class PinHashUtil {

    private static final String PBKDF2_ALG = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;

    private PinHashUtil() {}

    public static String generateRandomString(int length) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public static String hashGeneratedAndPin(String generated, String pin) {
        byte[] salt = randomSalt();
        byte[] hash = pbkdf2((generated + ":" + pin).toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        return "pbkdf2$" + ITERATIONS + "$" + base64(salt) + "$" + base64(hash);
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALG);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 failure", e);
        }
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Vérifie si un PIN fourni correspond au hash stocké.
     * 
     * @param storedHash Le hash stocké au format "pbkdf2$iterations$salt$hash"
     * @param generated Le string généré (24 caractères) utilisé lors de la création du hash
     * @param providedPin Le PIN fourni par l'utilisateur à vérifier
     * @return true si le PIN correspond, false sinon
     */
    public static boolean verifyPin(String storedHash, String generated, String providedPin) {
        try {
            // 1. Parser le hash stocké : format "pbkdf2$iterations$salt$hash"
            String[] parts = storedHash.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
                return false;
            }
            
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = base64Decode(parts[2]);
            byte[] storedHashBytes = base64Decode(parts[3]);
            
            // 2. Recalculer le hash avec le PIN fourni
            byte[] computedHash = pbkdf2((generated + ":" + providedPin).toCharArray(), salt, iterations, KEY_LENGTH);
            
            // 3. Comparer les hash (comparaison constante pour éviter les attaques par timing)
            return constantTimeEquals(storedHashBytes, computedHash);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] base64Decode(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Comparaison constante dans le temps pour éviter les attaques par timing.
     */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}


