package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dto.UserDto;
import org.akuunda.akuundawallet.keycloak.api.dto.UserResponse;
import org.akuunda.akuundawallet.keycloak.api.service.UserService;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.wallet.api.dao.MeldTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.MeldTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMeldServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation du client Meld pour interagir avec l'API Meld.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AkuundaMeldServiceClientImpl implements AkuundaMeldServiceClient {

    public static final String SESSION_WIDGET = "/crypto/session/widget";
    public static final String SERVICE_PROVIDERS_PROPERTIES = "/service-providers/properties/";
    public static final String SERVICE_PROVIDERS_LIMITS_FIAT_CURRENCY_PURCHASES = "/service-providers/limits/fiat-currency-purchases";
    public static final String PAYMENTS_CRYPTO_QUOTE = "/payments/crypto/quote";
    public static final String WEBHOOK_URL = "/api/internal/v1/meld/webhook";
    public static final String PAYMENTS_TRANSACTIONS = "/payments/transactions/";

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final MeldTransactionRepository meldTransactionRepository;
    private final OperationRepository operationRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final TransactionNotificationHelper transactionNotificationHelper;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Value("${meld.api.base-url}")
    private String meldBaseUrl;

    @Value("${server.url}")
    private String serverBaseUrl;

    @Value("${akuunda.web-pay.base-url}")
    private String webPayBaseUrl;

    @Value("${meld.api.key}")
    private String meldApiKey;

    private static final String MELD_VERSION = "2025-03-04";

    // ==========================================
    // Étape 2 : Country defaults, Fiat currencies, Payment methods
    // ==========================================

    @Override
    public ResponseEntity<List<MeldCountryDefaults>> getCountryDefaults(String countryCode) {
        return getList(SERVICE_PROVIDERS_PROPERTIES + "defaults/by-country?countries=" + countryCode,
                MeldCountryDefaults.class);
    }

    @Override
    public ResponseEntity<List<MeldFiatCurrency>> getFiatCurrencies(String countryCode) {
        return getList(SERVICE_PROVIDERS_PROPERTIES + "fiat-currencies?countries=" +
                countryCode + "&accountFilter=true", MeldFiatCurrency.class);
    }

    @Override
    public ResponseEntity<List<MeldPaymentMethod>> getPaymentMethods(String fiatCurrency) {
        return getList(SERVICE_PROVIDERS_PROPERTIES + "payment-methods?fiatCurrencies=" +
                fiatCurrency + "&accountFilter=true", MeldPaymentMethod.class);
    }

    // ==========================================
    // Étape 3 : Crypto currencies
    // ==========================================
    @Override
    public ResponseEntity<MeldCryptoCurrenciesResponse> getCryptoCurrencies(String countryCode) {
        HttpRequest req = baseRequest(meldBaseUrl +
                SERVICE_PROVIDERS_PROPERTIES + "crypto-currencies?countries=" +
                countryCode + "&accountFilter=true").GET().build();
        return send(req, MeldCryptoCurrenciesResponse.class);
    }

    // ==========================================
    // Étape 4 : Purchase limits
    // ==========================================
    @Override
    public ResponseEntity<List<MeldPurchaseLimit>> getPurchaseLimits() {
        HttpRequest req = baseRequest(meldBaseUrl +
                SERVICE_PROVIDERS_LIMITS_FIAT_CURRENCY_PURCHASES + "?accountFilter=true").GET().build();
        return sendList(req, MeldPurchaseLimit.class);
    }

    // ==========================================
    // Étape 5 : Get real-time quotes
    // ==========================================
    @Override
    public ResponseEntity<MeldQuoteResponse> getMeldQuotes(String username, String sourceCurrency,
                                                           String destCurrency, String amount,
                                                           String countryCode) {
        return getMeldQuotes(username, sourceCurrency, destCurrency, amount, countryCode, null);
    }

    @Override
    public ResponseEntity<MeldQuoteResponse> getMeldQuotes(String username, String sourceCurrency,
                                                           String destCurrency, String amount,
                                                           String countryCode, String serviceProvider) {
        try {
            var userResponse = userService.getUser(username);
            if (!userResponse.getStatusCode().is2xxSuccessful()
                    || userResponse.getBody() == null
                    || userResponse.getBody().data().getWallets().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            UserDto userDto = userResponse.getBody().data();
            String walletAddress = userDto.getWallets().get(0).getWalletAddress();

            MeldQuoteRequest qr = new MeldQuoteRequest();
            qr.setSourceAmount(amount != null ? Double.parseDouble(amount) : null);
            qr.setSourceCurrencyCode(sourceCurrency);
            qr.setDestinationCurrencyCode(destCurrency);
            qr.setCountryCode(countryCode);
            qr.setWalletAddress(walletAddress);
            if (serviceProvider != null && !serviceProvider.isBlank()) {
                qr.setServiceProviders(java.util.List.of(serviceProvider.toUpperCase()));
            }

            HttpRequest req = post(meldBaseUrl + PAYMENTS_CRYPTO_QUOTE, qr);
            ResponseEntity<MeldQuoteResponse> response = send(req, MeldQuoteResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                response.getBody().applyRecommendations();
            }

            return response;
        } catch (Exception e) {
            log.error("Erreur getMeldQuotes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==========================================
    // Étape 6 : Create Session widget
    // ==========================================
    @Override
    public ResponseEntity<MeldSessionResponse> createMeldSession(String username, String externalCustomerId,
                                                                 String provider,
                                                                 String sourceCurrency, String destCurrency,
                                                                 String sourceAmount, String sessionType,
                                                                 String countryCode, String walletAddress,
                                                                 String clientIpAddress) {
        // externalCustomerId est ce que Meld reçoit comme identifiant "client" (typiquement un
        // email côté checkout public, repris ensuite par Mercuryo / Transak pour pré-remplir
        // leur widget). Si l'appelant n'en fournit pas, on retombe sur le `username` marchand
        // pour préserver le comportement historique des autres flux.
        String effectiveExternalCustomerId = (externalCustomerId != null && !externalCustomerId.isBlank())
                ? externalCustomerId.trim()
                : username;
        try {
            log.info("🚀 createMeldSession called with:");
            log.info("   username (Akuunda lookup): {}", username);
            log.info("   externalCustomerId (envoye a Meld): {}", effectiveExternalCustomerId);
            log.info("   provider: {}", provider);
            log.info("   sourceCurrency: {}", sourceCurrency);
            log.info("   destCurrency: {}", destCurrency);
            log.info("   sourceAmount: {}", sourceAmount);
            log.info("   sessionType: {}", sessionType);
            log.info("   countryCode: {}", countryCode);
            log.info("   walletAddress: {}", walletAddress);
            log.info("   clientIpAddress: {}", clientIpAddress);

            String redirectUrl = webPayBaseUrl;

            var userResponse = userService.getUser(username);
            if (!userResponse.getStatusCode().is2xxSuccessful()
                    || userResponse.getBody() == null
                    || userResponse.getBody().data().getWallets().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            UserResponse data = userResponse.getBody();
            if (data == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            UserDto userDto = data.data();
            WalletUserDto walletDto = userDto.getWallets().get(0);

            String effectiveWalletAddress = (walletAddress != null && !walletAddress.isBlank())
                    ? walletAddress
                    : walletDto.getWalletAddress();

            // ★ Construire SessionData avec clientIpAddress
            SessionData sd = new SessionData();
            sd.setClientIpAddress(clientIpAddress);  // ★ IMPORTANT: Mettre en premier pour UNLIMIT
            sd.setWalletAddress(effectiveWalletAddress);
            sd.setCountryCode(countryCode);
            sd.setSourceCurrencyCode(sourceCurrency);
            sd.setDestinationCurrencyCode(destCurrency);
            sd.setSourceAmount(sourceAmount);
            sd.setServiceProvider(provider);
            sd.setRedirectUrl(redirectUrl);

            // Log pour vérifier que clientIpAddress est bien set
            log.info("✅ SessionData.clientIpAddress = {}", sd.getClientIpAddress());

            if (clientIpAddress != null && !clientIpAddress.isBlank()) {
                log.info("🌐 clientIpAddress is SET for provider {}: {}", provider, clientIpAddress);
            } else {
                log.warn("⚠️ clientIpAddress is NULL/EMPTY for provider {} - UNLIMIT will fail!", provider);
            }

            MeldSessionRequest sr = new MeldSessionRequest();
            sr.setSessionType(sessionType);
            sr.setExternalCustomerId(effectiveExternalCustomerId);
            sr.setExternalSessionId(UUID.randomUUID().toString());
            sr.setSessionData(sd);

            // ★ LOG CRITIQUE: Voir exactement ce qui est envoyé à Meld
            try {
                String sessionDataJson = objectMapper.writeValueAsString(sd);
                String fullRequestJson = objectMapper.writeValueAsString(sr);
                log.info("📤 Meld sessionData JSON: {}", sessionDataJson);
                log.info("📤 Meld FULL request JSON: {}", fullRequestJson);
            } catch (Exception e) {
                log.warn("Cannot log request JSON", e);
            }

            HttpRequest req = post(meldBaseUrl + SESSION_WIDGET, sr);

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            
            log.info("📥 Meld response status: {}", res.statusCode());
            log.info("📥 Meld response body: {}", res.body());

            if (res.statusCode() == 200) {
                MeldSessionResponse sessionResponse = objectMapper.readValue(res.body(),
                        MeldSessionResponse.class);

                // --- Création de la transaction en base ---
                // Conserve externalCustomerId aligné avec ce qui a été envoyé à Meld pour
                // pouvoir corréler facilement les webhooks et les opérations en BDD.
                MeldTransaction transaction = MeldTransaction.builder()
                        .transactionId(sessionResponse.getId())
                        .externalCustomerId(effectiveExternalCustomerId)
                        .status("PENDING")
                        .sourceCurrencyCode(sourceCurrency)
                        .destinationCurrencyCode(destCurrency)
                        .sourceAmount(new java.math.BigDecimal(sourceAmount))
                        .walletAddress(effectiveWalletAddress)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                meldTransactionRepository.save(transaction);

                // enregistrer dans la table operation
                Operation operation = new Operation();
                operation.setCreatedAt(LocalDateTime.now());
                operation.setDesignation(sessionType.equals("BUY") ? "MELD_ON_RAMP" : "MELD_OFF_RAMP");
                operation.setTransactionType(sessionType.equals("BUY") ? "Dépôt" : "Retrait");
                operation.setProvider("Meld");
                operation.setType(sessionType.equals("BUY") ? "CREDIT" : "DEBIT");
                operation.setStatus("PENDING");
                operation.setUsername(username);
                operation.setDevise(sourceCurrency);
                operation.setAmount(Double.valueOf(sourceAmount));
                operation.setOperationHash(sessionResponse.getId());
                operationRepository.save(operation);

                // If using a custom wallet address, check if it's a one-time payment link and update status
                if (walletAddress != null && !walletAddress.isBlank()) {
                    oneTimePaymentLinkRepository.findByCreate2WalletAddress(walletAddress)
                            .filter(link -> "CREATED".equals(link.getStatus()))
                            .ifPresent(link -> {
                                link.setStatus("PENDING");
                                link.setYellowCardTransactionId(sessionResponse.getId());
                                oneTimePaymentLinkRepository.save(link);
                                log.info("✅ One-time payment link {} status updated to PENDING (Meld session: {})",
                                        link.getUniqueCode(), sessionResponse.getId());
                            });
                }

                log.info("✅ Meld session created successfully: {}", sessionResponse.getId());
                return ResponseEntity.ok(sessionResponse);
            }

            log.error("❌ Meld session creation failed - status: {}, body: {}", res.statusCode(), res.body());
            return ResponseEntity.status(res.statusCode()).build();
        } catch (Exception e) {
            log.error("❌ Exception in createMeldSession", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==========================================
    // Étape 7 : Track Transaction
    // ==========================================

    @Override
    public ResponseEntity<MeldTransactionDetails> getTransactionDetails(String transactionId) {
        try {
            String url = meldBaseUrl + PAYMENTS_TRANSACTIONS + transactionId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", basicAuth())
                    .header("Meld-Version", MELD_VERSION)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                MeldTransactionDetails details = objectMapper.readValue(response.body(),
                        MeldTransactionDetails.class);

                // Récupérer la transaction existante
                MeldTransaction transaction = meldTransactionRepository
                        .findByTransactionId(transactionId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Transaction non trouvée pour transactionId=" + transactionId));
                // Mettre à jour les champs pertinents
                transaction.setStatus(details.getStatus());
                transaction.setDestinationAmount(details.getDestinationAmount());
                transaction.setUpdatedAt(LocalDateTime.now());
                meldTransactionRepository.saveAndFlush(transaction);

                // Mapper le statut Meld vers le statut interne Akuunda
                String internalStatus = mapMeldStatus(details.getStatus());

                // Mettre à jour la table Operation
                Operation operation = operationRepository.findByOperationHash(transactionId);
                if (operation != null) {
                    String previousStatus = operation.getStatus();
                    operation.setStatus(internalStatus);
                    operation.setUpdatedAt(LocalDateTime.now());

                    // Renseigner providerAmount / providerDevise depuis Meld
                    if (details.getDestinationAmount() != null) {
                        operation.setProviderAmount(details.getDestinationAmount().doubleValue());
                    }
                    if (details.getDestinationCurrencyCode() != null) {
                        operation.setProviderDevise(details.getDestinationCurrencyCode());
                    }
                    operationRepository.saveAndFlush(operation);

                    // Mettre à jour le wallet si la transaction passe en VALIDEE
                    if ("VALIDEE".equals(internalStatus) && !"VALIDEE".equals(previousStatus)) {
                        updateWalletForOperation(operation);
                    }
                }
                return ResponseEntity.ok(details);
            }

            return ResponseEntity.status(response.statusCode()).build();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des détails de transaction Meld: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==========================================
    // Étape 7b : Search Transactions (filtres)
    // ==========================================

    @Override
    public ResponseEntity<List<MeldTransactionDetails>> searchTransactions(
            String statuses, String externalCustomerIds, Integer limit) {
        try {
            StringBuilder url = new StringBuilder(meldBaseUrl + "/payments/transactions?");

            if (statuses != null && !statuses.isBlank()) {
                url.append("statuses=").append(statuses).append("&");
            }
            if (externalCustomerIds != null && !externalCustomerIds.isBlank()) {
                url.append("externalCustomerIds=").append(externalCustomerIds).append("&");
            }
            if (limit != null) {
                url.append("limit=").append(limit).append("&");
            }

            // Retirer le dernier & ou ?
            String finalUrl = url.toString().replaceAll("[&?]$", "");

            HttpRequest request = baseRequest(finalUrl).GET().build();
            return sendList(request, MeldTransactionDetails.class);

        } catch (Exception e) {
            log.error("Erreur searchTransactions Meld: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==========================================
    // Étape 7c : Get Transaction by Session ID
    // ==========================================

    @Override
    public ResponseEntity<MeldTransactionDetails> getTransactionBySessionId(String sessionId) {
        try {
            String url = meldBaseUrl + "/payments/transactions/sessions/" + sessionId;

            HttpRequest request = baseRequest(url).GET().build();
            return send(request, MeldTransactionDetails.class);

        } catch (Exception e) {
            log.error("Erreur getTransactionBySessionId Meld: sessionId={}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ==========================================
    // Helper : Mapping statut Meld -> Akuunda
    // ==========================================

    private String mapMeldStatus(String meldStatus) {
        if (meldStatus == null) return "EN ATTENTE";
        return switch (meldStatus.toUpperCase()) {
            case "SETTLED" -> "VALIDEE";
            case "FAILED" -> "ECHOUEE";
            case "CANCELLED" -> "ANNULEE";
            case "PENDING", "SETTLING" -> "EN ATTENTE";
            default -> meldStatus;
        };
    }

    // ==========================================
    // Helper : Mise à jour wallet après validation
    // ==========================================

    private void updateWalletForOperation(Operation operation) {
        try {
            Users user = userRepository.getUsersByUsername(operation.getUsername());
            if (user == null) {
                log.error("Utilisateur non trouvé pour mise à jour wallet: {}", operation.getUsername());
                return;
            }

            Wallet wallet = walletRepository.findWalletByUsers(user).stream().findFirst().orElse(null);
            if (wallet == null) {
                log.error("Wallet non trouvé pour l'utilisateur: {}", operation.getUsername());
                return;
            }

            double amount = operation.getAmount() != null ? operation.getAmount() : 0.0;
            double converted = operation.getConvertedAmount() != null ? operation.getConvertedAmount() : 0.0;

            switch (operation.getType().toUpperCase()) {
                case "CREDIT" -> {
                    wallet.setBalance(wallet.getBalance() + amount);
                    wallet.setDeviseBalance(wallet.getDeviseBalance() + converted);
                }
                case "DEBIT" -> {
                    wallet.setBalance(wallet.getBalance() - amount);
                    wallet.setDeviseBalance(wallet.getDeviseBalance() - converted);
                }
                default -> {
                    log.error("Type d'opération invalide pour Meld: {}", operation.getType());
                    return;
                }
            }
            walletRepository.saveAndFlush(wallet);
            log.info("✅ Wallet mis à jour pour {} — type={}, amount={}, devise={}",
                    operation.getUsername(), operation.getType(), amount, operation.getDevise());

            // Notification push
            try {
                String label = "CREDIT".equalsIgnoreCase(operation.getType()) ? "Fonds reçus" : "Envoi confirmé";
                String amountInfo = operation.getProviderAmount() + " " + operation.getProviderDevise()
                        + " (" + operation.getAmount() + " " + operation.getDevise() + ")";
                transactionNotificationHelper.notifyTransactionSuccess(
                        operation.getUsername(),
                        label + " : " + amountInfo,
                        operation.getOperationHash()
                );
            } catch (Exception e) {
                log.warn("⚠️ Échec notification Meld wallet update: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de la mise à jour du wallet Meld pour {}: {}",
                    operation.getUsername(), e.getMessage(), e);
        }
    }

    // ==========================================
    // Helper : Auth + Requêtes HTTP
    // ==========================================

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

    private <T> ResponseEntity<List<T>> sendList(HttpRequest request, Class<T> clazz) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
                List<T> body = objectMapper.readValue(response.body(), type);
                return ResponseEntity.ok(body);
            } else {
                log.error("Meld API list error ({}): {}", response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Erreur Meld API list", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private <T> ResponseEntity<List<T>> getList(String path, Class<T> clazz) {
        HttpRequest req = baseRequest(meldBaseUrl + path).GET().build();
        return sendList(req, clazz);
    }
}
