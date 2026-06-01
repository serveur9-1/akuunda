package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.MerchantApiKeyRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantApiKeyRequest;
import org.akuunda.akuundawallet.wallet.api.entities.MerchantApiKey;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.service.MerchantApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantApiKeyServiceImpl implements MerchantApiKeyService {

    private final MerchantApiKeyRepository merchantApiKeyRepository;
    private final UserRepository userRepository;
    private final PermanentLinkRepository permanentLinkRepository;

    private static final int API_KEY_LENGTH = 32;
    private static final int API_SECRET_LENGTH = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public ResponseEntity<?> createApiKey(String username, String merchantSlug, MerchantApiKeyRequest request) {
        try {
            log.info("🔵 Creating API key for user: {} and slug: {}", username, merchantSlug);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.warn("⚠️ User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            PermanentLink permanentLink = permanentLinkRepository.findByMerchantSlug(merchantSlug).orElse(null);
            if (permanentLink == null) {
                log.warn("⚠️ Permanent link not found: {}", merchantSlug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Permanent link not found");
            }

            // Verify the user is the creator
            if (!permanentLink.getCreator().getUsername().equals(username)) {
                log.warn("⚠️ User {} is not the creator of permanent link {}", username, merchantSlug);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not the creator of this permanent link");
            }

            String apiKey = "sk_live_" + generateSecureAlphanumeric(API_KEY_LENGTH);
            String apiSecret = generateSecureAlphanumeric(API_SECRET_LENGTH);

            String webhookUrl = request != null ? request.getWebhookUrl() : null;
            String callbackUrl = request != null ? request.getCallbackUrl() : null;
            String cancelUrl = request != null ? request.getCancelUrl() : null;

            MerchantApiKey merchantApiKey = MerchantApiKey.builder()
                    .apiKey(apiKey)
                    .apiSecret(apiSecret)
                    .merchant(user)
                    .permanentLink(permanentLink)
                    .webhookUrl(webhookUrl)
                    .callbackUrl(callbackUrl)
                    .cancelUrl(cancelUrl)
                    .isActive(true)
                    .build();

            merchantApiKeyRepository.save(merchantApiKey);
            log.info("✅ API key created for user: {} and slug: {}", username, merchantSlug);

            // Return API key + secret (secret shown only ONCE)
            Map<String, Object> result = new HashMap<>();
            result.put("apiKey", apiKey);
            result.put("apiSecret", apiSecret);
            result.put("merchantSlug", merchantSlug);
            result.put("message", "Save the apiSecret securely — it will not be shown again.");

            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (Exception e) {
            log.error("❌ Error creating API key for user: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Override
    public ResponseEntity<?> listApiKeys(String username) {
        try {
            log.info("🔍 Listing API keys for user: {}", username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.warn("⚠️ User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            List<MerchantApiKey> keys = merchantApiKeyRepository.findByMerchantOrderByCreatedAtDesc(user);

            List<Map<String, Object>> result = keys.stream().map(key -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", key.getId());
                entry.put("apiKey", key.getApiKey());
                entry.put("merchantSlug", key.getPermanentLink().getMerchantSlug());
                entry.put("webhookUrl", key.getWebhookUrl());
                entry.put("callbackUrl", key.getCallbackUrl());
                entry.put("cancelUrl", key.getCancelUrl());
                entry.put("isActive", key.getIsActive());
                entry.put("createdAt", key.getCreatedAt());
                return entry;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Error listing API keys for user: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @Override
    public ResponseEntity<?> revokeApiKey(String username, Long apiKeyId) {
        try {
            log.info("🔴 Revoking API key {} for user: {}", apiKeyId, username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.warn("⚠️ User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            MerchantApiKey merchantApiKey = merchantApiKeyRepository.findById(apiKeyId).orElse(null);
            if (merchantApiKey == null) {
                log.warn("⚠️ API key not found: {}", apiKeyId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("API key not found");
            }

            if (!merchantApiKey.getMerchant().getUsername().equals(username)) {
                log.warn("⚠️ User {} does not own API key {}", username, apiKeyId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not own this API key");
            }

            merchantApiKey.setIsActive(false);
            merchantApiKeyRepository.save(merchantApiKey);
            log.info("✅ API key {} revoked for user: {}", apiKeyId, username);

            return ResponseEntity.ok("API key revoked successfully");

        } catch (Exception e) {
            log.error("❌ Error revoking API key {} for user: {}", apiKeyId, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    private String generateSecureAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
