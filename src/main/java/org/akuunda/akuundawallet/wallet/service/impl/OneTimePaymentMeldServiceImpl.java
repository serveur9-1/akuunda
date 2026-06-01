package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.MeldTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentQuoteRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.MeldTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.service.OneTimePaymentMeldService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service dédié à l'intégration Meld pour les liens de paiement unique (one-time payment links).
 * Permet au payeur de récupérer des quotes et d'initier un paiement via Meld,
 * les USDC étant envoyés sur le wallet CREATE2 temporaire du lien.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OneTimePaymentMeldServiceImpl implements OneTimePaymentMeldService {

    private static final String PAYMENTS_CRYPTO_QUOTE = "/payments/crypto/quote";
    private static final String SESSION_WIDGET = "/crypto/session/widget";
    private static final String WEBHOOK_URL = "/api/internal/v1/meld/webhook";
    private static final String MELD_VERSION = "2025-03-04";
    private static final String DESTINATION_CURRENCY = "USDC_POLYGON";
    private static final String SESSION_TYPE = "BUY";

    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final MeldTransactionRepository meldTransactionRepository;
    private final OperationRepository operationRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${meld.api.base-url}")
    private String meldBaseUrl;

    @Value("${meld.api.key}")
    private String meldApiKey;

    @Value("${server.url}")
    private String serverBaseUrl;

    @Value("${akuunda.web-pay.base-url}")
    private String webPayBaseUrl;

    @Override
    public ResponseEntity<MeldQuoteResponse> getQuotesForPaymentLink(String uniqueCode,
                                                                      OneTimePaymentQuoteRequest request) {
        try {
            Optional<OneTimePaymentLink> linkOpt = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode);
            if (linkOpt.isEmpty()) {
                log.warn("One-time payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            OneTimePaymentLink link = linkOpt.get();

            if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("One-time payment link expired: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            String walletAddress = link.getCreate2WalletAddress();

            MeldQuoteRequest qr = new MeldQuoteRequest();
            qr.setSourceAmount(request.getSourceAmount() != null ? Double.parseDouble(request.getSourceAmount()) : null);
            qr.setSourceCurrencyCode(request.getSourceCurrencyCode());
            qr.setDestinationCurrencyCode(request.getDestinationCurrencyCode());
            qr.setCountryCode(request.getCountryCode());
            qr.setWalletAddress(walletAddress);

            HttpRequest req = post(meldBaseUrl + PAYMENTS_CRYPTO_QUOTE, qr);
            ResponseEntity<MeldQuoteResponse> response = send(req, MeldQuoteResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                response.getBody().applyRecommendations();
            }

            return response;
        } catch (Exception e) {
            log.error("Erreur getQuotesForPaymentLink pour le lien: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<MeldSessionResponse> createPaymentSession(String uniqueCode,
                                                                     OneTimePaymentPayRequest request) {
        try {
            Optional<OneTimePaymentLink> linkOpt = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode);
            if (linkOpt.isEmpty()) {
                log.warn("One-time payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            OneTimePaymentLink link = linkOpt.get();

            if (!"CREATED".equals(link.getStatus())) {
                log.warn("One-time payment link {} is not in CREATED status (current: {})",
                        uniqueCode, link.getStatus());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            String creatorUsername = link.getCreator() != null ? link.getCreator().getUsername() : uniqueCode;
            String walletAddress = link.getCreate2WalletAddress();
            String redirectUrl = webPayBaseUrl + "/confirmation/" + uniqueCode;

            SessionData sd = new SessionData();
            sd.setWalletAddress(walletAddress);
            sd.setCountryCode(request.getCountryCode());
            sd.setSourceCurrencyCode(request.getSourceCurrencyCode());
            sd.setDestinationCurrencyCode(DESTINATION_CURRENCY);
            sd.setSourceAmount(request.getSourceAmount());
            sd.setServiceProvider(request.getServiceProvider());
            sd.setRedirectUrl(redirectUrl);

            MeldSessionRequest sr = new MeldSessionRequest();
            sr.setSessionType(SESSION_TYPE);
            sr.setExternalCustomerId(creatorUsername);
            sr.setExternalSessionId(UUID.randomUUID().toString());
            sr.setSessionData(sd);

            HttpRequest req = post(meldBaseUrl + SESSION_WIDGET, sr);
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                MeldSessionResponse sessionResponse = objectMapper.readValue(res.body(), MeldSessionResponse.class);

                MeldTransaction transaction = MeldTransaction.builder()
                        .transactionId(sessionResponse.getId())
                        .externalCustomerId(creatorUsername)
                        .status("PENDING")
                        .sourceCurrencyCode(request.getSourceCurrencyCode())
                        .destinationCurrencyCode(DESTINATION_CURRENCY)
                        .sourceAmount(new java.math.BigDecimal(request.getSourceAmount()))
                        .walletAddress(walletAddress)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                meldTransactionRepository.save(transaction);

                // ✅ CORRIGÉ : Ne PAS créer de DEBIT sur le marchand.
                // Le marchand (creator) n'est jamais débité dans un paiement par lien.
                // C'est le payeur externe qui paie via Meld.
                // L'opération CREDIT pour le marchand sera créée dans
                // PaymentDepositPollingTask.handleSimplePayment() quand les fonds arrivent.
                log.info("ℹ️ Pas d'opération DEBIT créée pour le marchand {} — le payeur externe paie via Meld",
                        creatorUsername);

                link.setStatus("PENDING");
                link.setYellowCardTransactionId(sessionResponse.getId());
                if (sessionResponse.getServiceProviderWidgetUrl() != null) {
                    link.setProviderWidgetUrl(sessionResponse.getServiceProviderWidgetUrl());
                }
                oneTimePaymentLinkRepository.save(link);

                // Build secure redirect URL (never expose provider URL to frontend)
                String payRedirectUrl = serverBaseUrl + "/api/internal/v1/pay-redirect/otpl/" + uniqueCode;
                sessionResponse.setPayRedirectUrl(payRedirectUrl);

                log.info("✅ One-time payment link {} initiated via Meld (session: {}, create2: {})",
                        uniqueCode, sessionResponse.getId(), walletAddress);

                return ResponseEntity.ok(sessionResponse);
            }

            log.error("Meld session creation failed for link {} (HTTP {}): {}", uniqueCode, res.statusCode(), res.body());
            return ResponseEntity.status(res.statusCode()).build();
        } catch (Exception e) {
            log.error("Erreur createPaymentSession pour le lien: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String basicAuth() {
        return "Basic " + meldApiKey;
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", basicAuth())
                .header("Content-Type", "application/json")
                .header("Meld-Version", MELD_VERSION);
    }

    private HttpRequest post(String url, Object body) {
        try {
            return baseRequest(url)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Erreur de sérialisation JSON", e);
        }
    }

    private <T> ResponseEntity<T> send(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                T body = objectMapper.readValue(response.body(), responseType);
                return ResponseEntity.status(response.statusCode()).body(body);
            } else {
                log.error("Meld API call failed ({}): {}", response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Erreur Meld API", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
