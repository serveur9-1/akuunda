package org.akuunda.akuundawallet.wallet.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@Component
public class WebhookSignatureVerifier {

    @Value("${kyrrex.webhook.secret}")
    private String webhookSecret;

    @Value("${kyrrex.webhook.signature-verification-enabled:true}")
    private boolean verificationEnabled;

    public void verify(String rawBody, String receivedSignature) {
        if (!verificationEnabled) {
            log.warn("Webhook signature verification DISABLED");
            return;
        }
        if (receivedSignature == null || receivedSignature.isBlank()) {
            throw new SecurityException("Missing webhook signature");
        }

        String expected = computeHmac(rawBody);

        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid webhook signature");
        }

        log.debug("Webhook signature OK");
    }

    private String computeHmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }
}
