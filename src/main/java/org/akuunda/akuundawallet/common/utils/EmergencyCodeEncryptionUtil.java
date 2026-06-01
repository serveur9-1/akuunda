package org.akuunda.akuundawallet.common.utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class EmergencyCodeEncryptionUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    // Clé par défaut (256 bits) - devrait être configurée dans application.properties en production
    private static final String DEFAULT_SECRET_KEY = "AkunndaEmergencyCodeSecretKeyForEncryption2024!SecureKeyForEmergencyCode";
    private static SecretKey secretKey;

    static {
        try {
            // Utiliser la clé par défaut (les 32 premiers bytes pour AES-256)
            byte[] keyBytes = DEFAULT_SECRET_KEY.substring(0, 32).getBytes();
            secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize encryption key", e);
        }
    }

    private EmergencyCodeEncryptionUtil() {}

    /**
     * Chiffre une chaîne de caractères de manière réversible
     * @param plainText Le texte à chiffrer
     * @return Le texte chiffré en Base64
     */
    public static String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Déchiffre une chaîne de caractères
     * @param encryptedText Le texte chiffré en Base64
     * @return Le texte déchiffré
     */
    public static String decrypt(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    /**
     * Génère une clé secrète AES (utilisé pour la configuration initiale)
     * @return Une clé secrète AES
     */
    public static SecretKey generateSecretKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(256);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to generate secret key", e);
        }
    }
}
