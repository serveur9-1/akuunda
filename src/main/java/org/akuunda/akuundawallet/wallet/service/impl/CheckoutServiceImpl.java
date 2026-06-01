package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.CheckoutSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.MerchantApiKeyRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutSessionResponse;
import org.akuunda.akuundawallet.wallet.api.entities.CheckoutSession;
import org.akuunda.akuundawallet.wallet.api.entities.MerchantApiKey;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.service.CheckoutService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutServiceImpl implements CheckoutService {

    private final MerchantApiKeyRepository merchantApiKeyRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ObjectMapper objectMapper;

    @Value("${akuunda.frontend.base-url:https://qr.akuunda-pay.io}")
    private String frontendBaseUrl;

    private static final int CHECKOUT_CODE_LENGTH = 12;
    private static final long CHECKOUT_EXPIRY_HOURS = 1;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public ResponseEntity<CheckoutResponse> createCheckout(String apiKey, CheckoutRequest request) {
        try {
            log.info("🔵 Creating checkout session for API key: {}...", apiKey != null && apiKey.length() > 10 ? apiKey.substring(0, 10) : apiKey);

            MerchantApiKey merchantApiKey = merchantApiKeyRepository.findByApiKeyAndIsActiveTrue(apiKey).orElse(null);
            if (merchantApiKey == null) {
                log.warn("⚠️ Invalid or inactive API key: {}", apiKey);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
            }

            PermanentLink permanentLink = merchantApiKey.getPermanentLink();

            String checkoutCode = "ch_" + generateSecureAlphanumeric(CHECKOUT_CODE_LENGTH);

            String metadataJson = null;
            if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
                try {
                    metadataJson = objectMapper.writeValueAsString(request.getMetadata());
                } catch (JsonProcessingException e) {
                    log.warn("⚠️ Could not serialize metadata: {}", e.getMessage());
                }
            }

            // Prefer request-level URLs, fall back to API key defaults
            String callbackUrl = request.getCallbackUrl() != null ? request.getCallbackUrl() : merchantApiKey.getCallbackUrl();
            String cancelUrl = request.getCancelUrl() != null ? request.getCancelUrl() : merchantApiKey.getCancelUrl();
            String webhookUrl = request.getWebhookUrl() != null ? request.getWebhookUrl() : merchantApiKey.getWebhookUrl();

            LocalDateTime expiresAt = LocalDateTime.now().plusHours(CHECKOUT_EXPIRY_HOURS);

            CheckoutSession checkoutSession = CheckoutSession.builder()
                    .checkoutCode(checkoutCode)
                    .merchantApiKey(merchantApiKey)
                    .permanentLink(permanentLink)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .merchantReference(request.getReference())
                    .description(request.getDescription())
                    .callbackUrl(callbackUrl)
                    .cancelUrl(cancelUrl)
                    .webhookUrl(webhookUrl)
                    .metadataJson(metadataJson)
                    .status("CREATED")
                    .webhookSent(false)
                    .expiresAt(expiresAt)
                    .build();

            checkoutSessionRepository.save(checkoutSession);
            log.info("✅ Checkout session created: {}", checkoutCode);

            String checkoutUrl = frontendBaseUrl + "/checkout/" + checkoutCode;

            CheckoutResponse response = CheckoutResponse.builder()
                    .checkoutUrl(checkoutUrl)
                    .checkoutCode(checkoutCode)
                    .expiresAt(expiresAt)
                    .reference(request.getReference())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("❌ Error creating checkout session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<CheckoutSessionResponse> getCheckoutByCode(String checkoutCode) {
        try {
            log.info("🔍 Getting checkout session: {}", checkoutCode);

            CheckoutSession session = checkoutSessionRepository.findByCheckoutCode(checkoutCode).orElse(null);
            if (session == null) {
                log.warn("⚠️ Checkout session not found: {}", checkoutCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            // Auto-expire if expired and still CREATED
            if ("CREATED".equals(session.getStatus()) && session.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.info("⏰ Checkout session expired: {}", checkoutCode);
                session.setStatus("EXPIRED");
                checkoutSessionRepository.save(session);
            }

            PermanentLink permanentLink = session.getPermanentLink();
            String merchantName = buildMerchantName(permanentLink);

            CheckoutSessionResponse response = CheckoutSessionResponse.builder()
                    .checkoutCode(session.getCheckoutCode())
                    .amount(session.getAmount())
                    .currency(session.getCurrency())
                    .reference(session.getMerchantReference())
                    .description(session.getDescription())
                    .status(session.getStatus())
                    .merchantSlug(permanentLink != null ? permanentLink.getMerchantSlug() : null)
                    .merchantName(merchantName)
                    .callbackUrl(session.getCallbackUrl())
                    .cancelUrl(session.getCancelUrl())
                    .expiresAt(session.getExpiresAt())
                    .createdAt(session.getCreatedAt())
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error getting checkout session: {}", checkoutCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private String buildMerchantName(PermanentLink permanentLink) {
        if (permanentLink == null || permanentLink.getCreator() == null) return null;
        String firstname = permanentLink.getCreator().getFirstname();
        String lastname = permanentLink.getCreator().getLastname();
        if (firstname == null) return null;
        if (lastname != null && !lastname.isEmpty()) {
            return firstname + " " + lastname.charAt(0) + ".";
        }
        return firstname;
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
