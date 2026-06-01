package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.CheckoutSessionRepository;
import org.akuunda.akuundawallet.wallet.api.entities.CheckoutSession;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.service.WebhookService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookServiceImpl implements WebhookService {

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final ObjectMapper objectMapper;

    private static final int WEBHOOK_TIMEOUT_MS = 10_000;

    @Override
    public void sendPaymentWebhook(CheckoutSession checkoutSession) {
        sendEventWebhook(checkoutSession, "payment.completed");
    }

    @Override
    public void sendEventWebhook(CheckoutSession checkoutSession, String eventName) {
        String webhookUrl = checkoutSession.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("ℹ️ No webhook URL configured for checkout: {}", checkoutSession.getCheckoutCode());
            return;
        }

        try {
            PermanentLinkSession paymentSession = checkoutSession.getPaymentSession();
            PermanentLink permanentLink = checkoutSession.getPermanentLink();

            // Build data payload
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("checkoutCode", checkoutSession.getCheckoutCode());
            data.put("reference", checkoutSession.getMerchantReference());
            data.put("amount", checkoutSession.getAmount());
            data.put("currency", checkoutSession.getCurrency());
            data.put("status", checkoutSession.getStatus());
            data.put("mode", checkoutSession.getMode());

            if (paymentSession != null) {
                data.put("payerName", paymentSession.getPayerName());
                data.put("payerEmail", paymentSession.getPayerEmail());
                data.put("payerPhone", paymentSession.getPayerPhone());
                data.put("paymentProvider", paymentSession.getPaymentProvider());
            }

            LocalDateTime paidAt = checkoutSession.getPaidAt();
            data.put("paidAt", paidAt != null ? paidAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);

            if (checkoutSession.getRefundedAmount() != null) {
                data.put("refundedAmount", checkoutSession.getRefundedAmount());
                data.put("refundedAt", checkoutSession.getRefundedAt() != null
                        ? checkoutSession.getRefundedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
                data.put("refundReason", checkoutSession.getRefundReason());
            }

            data.put("merchantSlug", permanentLink != null ? permanentLink.getMerchantSlug() : null);

            // Deserialize metadata if present
            if (checkoutSession.getMetadataJson() != null && !checkoutSession.getMetadataJson().isBlank()) {
                try {
                    Map<?, ?> metadata = objectMapper.readValue(checkoutSession.getMetadataJson(), Map.class);
                    data.put("metadata", metadata);
                } catch (Exception e) {
                    log.warn("⚠️ Could not deserialize metadata JSON for checkout {}: {}", checkoutSession.getCheckoutCode(), e.getMessage());
                }
            }

            // Build full payload (without signature)
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", eventName != null && !eventName.isBlank() ? eventName : "payment.completed");
            payload.put("data", data);
            payload.put("timestamp", Instant.now().toString());

            String payloadJson = objectMapper.writeValueAsString(payload);

            // Sign with HMAC-SHA256 — only sent in header, not in the body
            String signature = hmacSha256Hex(payloadJson, checkoutSession.getMerchantApiKey().getApiSecret());

            // Build HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Akuunda-Signature", signature);

            HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);

            RestTemplate restTemplate = buildRestTemplate();
            restTemplate.postForEntity(webhookUrl, entity, String.class);

            log.info("✅ Webhook [{}] sent successfully for checkout: {}", payload.get("event"), checkoutSession.getCheckoutCode());

            // Mark webhook as sent (only for payment.completed events, refunds are not tracked here)
            if ("payment.completed".equals(payload.get("event"))) {
                checkoutSession.setWebhookSent(true);
                checkoutSession.setWebhookSentAt(LocalDateTime.now());
                checkoutSessionRepository.save(checkoutSession);
            }

        } catch (Exception e) {
            log.error("❌ Failed to send webhook for checkout: {}. Error: {}", checkoutSession.getCheckoutCode(), e.getMessage());
            // Do not rethrow — webhook failure must not block payment flow
        }
    }

    private String hmacSha256Hex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(WEBHOOK_TIMEOUT_MS);
        factory.setReadTimeout(WEBHOOK_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
