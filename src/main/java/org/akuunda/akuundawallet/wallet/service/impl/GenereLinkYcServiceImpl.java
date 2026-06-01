package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkResponse;
import org.akuunda.akuundawallet.wallet.service.GenereLinkYcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenereLinkYcServiceImpl implements GenereLinkYcService {

    @Value("${akuunda.generelink.yc.base-url:https://link.akuunda-pay.io}")
    private String genereLinkYcBaseUrl;

    private final ObjectMapper objectMapper;

    @Override
    public GenereLinkResponse generateLink(GenereLinkRequest request) {
        String url = genereLinkYcBaseUrl.replaceAll("/$", "") + "/api/payments/generate-link";
        log.info("📤 GenereLinkYc: calling {} for country={}", url, request.getCountry());

        try {
            Map<String, Object> body = new HashMap<>();

            if (request.getRecipient() != null) {
                Map<String, Object> recipient = new HashMap<>();
                if (request.getRecipient().getName() != null) recipient.put("name", request.getRecipient().getName());
                if (request.getRecipient().getPhone() != null) recipient.put("phone", request.getRecipient().getPhone());
                if (request.getRecipient().getEmail() != null) recipient.put("email", request.getRecipient().getEmail());
                body.put("recipient", recipient);
            }

            if (request.getSource() != null) {
                Map<String, Object> source = new HashMap<>();
                if (request.getSource().getAccountNumber() != null) source.put("accountNumber", request.getSource().getAccountNumber());
                if (request.getSource().getAccountType() != null) source.put("accountType", request.getSource().getAccountType());
                if (request.getSource().getNetworkId() != null) source.put("networkId", request.getSource().getNetworkId());
                if (request.getSource().getPhoneNumber() != null) source.put("phoneNumber", request.getSource().getPhoneNumber());
                body.put("source", source);
            }

            if (request.getAmount() != null) body.put("amount", request.getAmount());
            if (request.getCurrency() != null) body.put("currency", request.getCurrency());
            if (request.getCountry() != null) body.put("country", request.getCountry());
            if (request.getReason() != null) body.put("reason", request.getReason());
            if (request.getChannelId() != null) body.put("channelId", request.getChannelId());
            if (request.getSettlementWalletAddress() != null) {
                body.put("settlementWalletAddress", request.getSettlementWalletAddress());
            }
            if (request.getUniqueCode() != null) {
                body.put("uniqueCode", request.getUniqueCode());
            }

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int statusCode = httpResponse.statusCode();
            String responseBody = httpResponse.body();

            log.info("📥 GenereLinkYc: response status={}", statusCode);
            log.debug("📥 GenereLinkYc: response body={}", responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode data = root.has("data") ? root.get("data") : root;

                String transactionId = data.has("transactionId") ? data.get("transactionId").asText() : null;
                String paymentUrl = data.has("paymentUrl") ? data.get("paymentUrl").asText() : null;

                if (transactionId == null || paymentUrl == null) {
                    throw new RuntimeException("GenereLinkYc response missing transactionId or paymentUrl: " + responseBody);
                }

                log.info("✅ GenereLinkYc: transactionId={}, paymentUrl={}", transactionId, paymentUrl);
                return GenereLinkResponse.builder()
                        .transactionId(transactionId)
                        .paymentUrl(paymentUrl)
                        .build();
            } else {
                throw new RuntimeException("GenereLinkYc returned HTTP " + statusCode + ": " + responseBody);
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ GenereLinkYc: failed to call {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to call akuundapay_generelinkYC /generate-link: " + e.getMessage(), e);
        }
    }
}
