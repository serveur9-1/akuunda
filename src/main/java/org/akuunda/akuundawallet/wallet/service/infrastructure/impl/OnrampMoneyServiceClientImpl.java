package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.service.UserService;
import org.akuunda.akuundawallet.wallet.api.dao.OnrampTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CountryConfigDto;
import org.akuunda.akuundawallet.wallet.api.dto.OnrampMoneyResponse;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.QuoteRequestDTO;
import org.akuunda.akuundawallet.wallet.api.dto.external.QuotesResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.ResponseQuotes;
import org.akuunda.akuundawallet.wallet.api.entities.OnrampTransaction;
import org.akuunda.akuundawallet.wallet.api.requests.OnrampMoneyRequest;
import org.akuunda.akuundawallet.wallet.service.infrastructure.OnrampMoneyServiceClient;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnrampMoneyServiceClientImpl implements OnrampMoneyServiceClient {

    public static final String FETCH_ALL_COUNTRY_CONFIG = "/public/fetchAllCountryConfig";
    public static final String TRANSACTION_QUOTES = "/transaction/quotes";
    public static final String GENERATE_LINK = "/transaction/generateLink";

    private static final String COIN_CODE = "usdc";
    private static final String NETWORK = "matic20";
    private static final String CHAIN_ID = "137";
    private static final String TYPE = "1";
    private static final String LANG = "fr";
    private static final String MERCHANDISE_ID = "ORDER_FR_";

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final OnrampTransactionRepository onrampTransactionRepository;

    @Value("${onramp.money.base.url}")
    private String baseUrl;

    @Value("${X-ONRAMP-APIKEY}")
    private String apiKey;

    @Value("${X-ONRAMP-API_SECRET}")
    private String apiSecret;

    // ===============================
    // 🔹 Méthode principale : génération du lien widget
    // ===============================
    @Override
    public ResponseEntity<OnrampMoneyResponse> generateWidgetLink(OnrampMoneyRequest request) {
        final var user = userService.getUser(request.getUsername());
        if (user == null) {
            log.error("User not found: {}", request.getUsername());
            OnrampMoneyResponse response = new OnrampMoneyResponse();
            response.setData(null);
            response.setStatus("error");
            response.setMessage("User not found");
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        ResponseEntity<CountryConfigDto> countryConfig = fetchAllCountryConfig();
        if (!countryConfig.getStatusCode().is2xxSuccessful() || countryConfig.getBody() == null) {
            log.error("Failed to fetch country configurations");
            OnrampMoneyResponse response = new OnrampMoneyResponse();
            response.setData(null);
            response.setStatus("error");
            response.setMessage("Failed to fetch country configurations");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        var countryEntry = findCountryByCurrency(countryConfig.getBody(), request.getCurrency());
        if (countryEntry == null) {
            log.error("Currency not supported: {}", request.getCurrency());
            OnrampMoneyResponse response = new OnrampMoneyResponse();
            response.setData(null);
            response.setStatus("error");
            response.setMessage("Currency not supported: " + request.getCurrency());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        String fiatType = safeValue(countryEntry.getKey());
        if (user.getBody() == null|| user.getBody().data() == null  || user.getBody().data().getWallets() == null || user.getBody().data().getWallets().isEmpty()) {
            log.error("No wallet found for user: {}", request.getUsername());
            OnrampMoneyResponse response = new OnrampMoneyResponse();
            response.setData(null);
            response.setStatus("error");
            response.setMessage("No wallet found for user:" + request.getUsername());
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }

        var wallet = user.getBody().data().getWallets().get(0);
        String merchandiseId = MERCHANDISE_ID + System.currentTimeMillis();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("coinCode", safeValue(COIN_CODE));
            body.put("chainId", safeValue(CHAIN_ID));
            body.put("network", safeValue(NETWORK));
            body.put("fiatAmount", safeNumber(request.getFiatAmount()));
            body.put("fiatType", safeValue(fiatType));
            body.put("type", safeValue(TYPE));
            body.put("walletAddress", safeValue(wallet.getWalletAddress()));
            body.put("phoneNumber", safeValue(request.getPhoneNumber()));
            body.put("lang", safeValue(LANG));
            body.put("merchantRecognitionId", safeValue(merchandiseId));

            Map<String, Object> payloadMap = Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "body", body
            );

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            String payloadBase64 = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = generateHmacSHA512Signature(payloadBase64, apiSecret);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + GENERATE_LINK))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-ONRAMP-SIGNATURE", signature)
                    .header("X-ONRAMP-APIKEY", apiKey)
                    .header("X-ONRAMP-PAYLOAD", payloadBase64)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var onrampResponse = getOnrampMoneyResponse(request, response, fiatType, wallet, merchandiseId);
                OnrampMoneyResponse moneyResponse = new OnrampMoneyResponse();
                moneyResponse.setData(onrampResponse.getData());
                moneyResponse.setStatus("success");
                moneyResponse.setMessage("No wallet found for user:" + request.getUsername());
                return new ResponseEntity<>(moneyResponse, HttpStatus.OK);
            } else {
                log.error("Erreur lors de la génération du lien : {}", response.body());
                OnrampMoneyResponse moneyResponse = new OnrampMoneyResponse();
                moneyResponse.setData(null);
                moneyResponse.setStatus("error");
                moneyResponse.setMessage("Erreur lors de la génération du lien : " + response.body());
                return new ResponseEntity<>(moneyResponse, HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            log.error("Exception lors de la génération du lien", e);
            OnrampMoneyResponse moneyResponse = new OnrampMoneyResponse();
            moneyResponse.setData(null);
            moneyResponse.setStatus("error");
            moneyResponse.setMessage("Erreur lors de la génération du lien : " + e);
            return new ResponseEntity<>(moneyResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ===============================
    // 🔹 Récupération de la config pays
    // ===============================
    @Override
    public ResponseEntity<CountryConfigDto> fetchAllCountryConfig() {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + FETCH_ALL_COUNTRY_CONFIG))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.debug("Configurations des pays récupérées avec succès");
                log.info("Configurations des pays récupérées avec succès");
                CountryConfigDto.DataDto data = objectMapper.readValue(response.body(), CountryConfigDto.class).getData();
                CountryConfigDto countryConfigDto = new CountryConfigDto();
                countryConfigDto.setData(data);
                countryConfigDto.setStatus("success");
                countryConfigDto.setMessage("Configurations des pays récupérées avec succès");
                return new ResponseEntity<>(countryConfigDto, HttpStatus.OK);
            } else {
                log.error("Erreur lors de la récupération des configurations des pays : {}", response.body());
                CountryConfigDto countryConfigDto = new CountryConfigDto();
                countryConfigDto.setData(null);
                countryConfigDto.setStatus("error");
                countryConfigDto.setMessage(response.body());
                return new ResponseEntity<>(countryConfigDto, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Exception lors de la récupération des configurations des pays", e);
            CountryConfigDto countryConfigDto = new CountryConfigDto();
            countryConfigDto.setData(null);
            countryConfigDto.setStatus("error");
            countryConfigDto.setMessage("impossible de récupérer les configurations des pays : " + e.getMessage());
            return new ResponseEntity<>(countryConfigDto, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ===============================
    // 🔹 Vérification d'activation
    // ===============================
    @Override
    public ResponseEntity<Map<String, Integer>> verifyIfIsActive(String codePays) {
        log.info("verifyIfIsActive: {}", codePays);
        Map<String, Integer> paymentMethods = new HashMap<>();

        ResponseEntity<CountryConfigDto> countryConfig = fetchAllCountryConfig();
        if (!countryConfig.getStatusCode().is2xxSuccessful() || countryConfig.getBody() == null) {
            return ResponseEntity.badRequest().body(paymentMethods);
        }

        var entry = findCountryByCurrency(countryConfig.getBody(), codePays);
        if (entry == null) return ResponseEntity.badRequest().body(paymentMethods);

        if (entry.getValue().getIsActive() == 1) {
            paymentMethods = entry.getValue().getPaymentMethods();
            return ResponseEntity.ok(paymentMethods);
        }

        return ResponseEntity.badRequest().body(paymentMethods);
    }

    // ===============================
    // 🔹 Récupération des quotes
    // ===============================
    @Override
    public ResponseEntity<ResponseQuotes> getQuotes(QuoteRequestDTO request) {
        log.info("getQuotes : {}", request);

        try {
            ResponseEntity<CountryConfigDto> countryConfig = fetchAllCountryConfig();
            if (!countryConfig.getStatusCode().is2xxSuccessful() || countryConfig.getBody() == null) {
                ResponseQuotes responseQuotes = new ResponseQuotes();
                responseQuotes.setData(new QuotesResponse(0.0, 0.0));
                responseQuotes.setStatus("error");
                responseQuotes.setMessage("Failed to fetch country configurations");
                return new ResponseEntity<>(responseQuotes, HttpStatus.NOT_FOUND);
            }

            var entry = findCountryByCurrency(countryConfig.getBody(), request.getCurrency());
            if (entry == null) {
                ResponseQuotes responseQuotes = new ResponseQuotes();
                responseQuotes.setData(null);
                responseQuotes.setStatus("error");
                responseQuotes.setMessage("Bad request: Currency not supported");
                return new ResponseEntity<>(responseQuotes, HttpStatus.BAD_REQUEST);
            }

            String fiatType = entry.getKey();

            Map<String, Object> body = new HashMap<>();
            body.put("coinId", safeValue(CHAIN_ID));
            body.put("coinCode", safeValue(COIN_CODE));
            body.put("chainId", safeValue(CHAIN_ID));
            body.put("network", safeValue(NETWORK));
            body.put("fiatAmount", safeNumber(request.getFiatAmount()));
            body.put("fiatType", safeValue(fiatType));
            body.put("type", safeValue(TYPE));

            Map<String, Object> payload = Map.of(
                    "timestamp", System.currentTimeMillis(),
                    "body", body
            );

            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadBase64 = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = Hex.encodeHexString(new HmacUtils("HmacSHA512", apiSecret).hmac(payloadBase64));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + TRANSACTION_QUOTES))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-ONRAMP-APIKEY", apiKey)
                    .header("X-ONRAMP-SIGNATURE", signature)
                    .header("X-ONRAMP-PAYLOAD", payloadBase64)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return handleQuotesResponse(response.body(), request, response.statusCode());

        } catch (Exception e) {
            log.error("Erreur lors de l'appel à Onramp Quotes API", e);
            ResponseQuotes responseQuotes = new ResponseQuotes();
            responseQuotes.setData(null);
            responseQuotes.setStatus("error");
            responseQuotes.setMessage("internal server error: " + e.getMessage());
            return new ResponseEntity<>(responseQuotes, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<ResponseQuotes> handleQuotesResponse(String body, QuoteRequestDTO request, int statusCode)
            throws JsonProcessingException {
        if (statusCode < 200 || statusCode >= 300) {
            log.error("Erreur HTTP {}: {}", statusCode, body);
            ResponseQuotes responseQuotes = new ResponseQuotes();
            responseQuotes.setData(null);
            responseQuotes.setStatus("error");
            responseQuotes.setMessage("Erreur HTTP "+ body);
            return new ResponseEntity<>(responseQuotes, HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> responseMap = objectMapper.readValue(body, Map.class);
        if (Objects.equals(responseMap.get("status"), 1) && Objects.equals(responseMap.get("code"), 200)) {
            Map<String, Object> data = (Map<String, Object>) responseMap.get("data");
            double totalFees = getDoubleValue(data, "onrampFee")
                    + getDoubleValue(data, "clientFee")
                    + getDoubleValue(data, "gatewayFee")
                    + getDoubleValue(data, "gasFee");

            double totalWithFees = request.getFiatAmount() + totalFees;
            ResponseQuotes responseQuotes = new ResponseQuotes();
            responseQuotes.setData(new QuotesResponse(totalFees, totalWithFees));
            responseQuotes.setStatus("success");
            responseQuotes.setMessage("Quotes retrieved successfully");
            return new ResponseEntity<>(responseQuotes, HttpStatus.OK);
        }

        log.error("Réponse Onramp invalide : {}", body);
        ResponseQuotes responseQuotes = new ResponseQuotes();
        responseQuotes.setData(null);
        responseQuotes.setStatus("error");
        responseQuotes.setMessage("Erreur "+ body);
        return new ResponseEntity<>(responseQuotes, HttpStatus.BAD_REQUEST);
    }

    // ===============================
    // 🔹 Helpers
    // ===============================
    private double getDoubleValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private OnrampMoneyResponse getOnrampMoneyResponse(OnrampMoneyRequest request, HttpResponse<String> response,
                                                       String fiatType, WalletUserDto wallet, String merchandiseId)
            throws JsonProcessingException {

        OnrampMoneyResponse onrampResponse = objectMapper.readValue(response.body(), OnrampMoneyResponse.class);

        OnrampTransaction transaction = new OnrampTransaction();
        transaction.setCoinId(COIN_CODE);
        transaction.setChainId(CHAIN_ID);
        transaction.setNetwork(NETWORK);
        transaction.setFiatAmount(Double.valueOf(safeValue(request.getFiatAmount())));
        transaction.setFiatType(fiatType);
        transaction.setCodePays(request.getCurrency());
        transaction.setType(TYPE);
        transaction.setWalletId(wallet.getId());
        transaction.setUserName(request.getUsername());
        transaction.setPaymentMethod("ONRAMP_MONEY");
        transaction.setPhoneNumber(request.getPhoneNumber());
        transaction.setLang(LANG);
        transaction.setMerchantRecognitionId(merchandiseId);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setLink(onrampResponse.getData().getLink());
        transaction.setUrlHash(onrampResponse.getData().getUrlHash());

        onrampTransactionRepository.save(transaction);
        return onrampResponse;
    }

    private Map.Entry<String, CountryConfigDto.CountryDto> findCountryByCurrency(CountryConfigDto config, String currency) {
        if (config != null && config.getData() != null && config.getData().getBuy() != null) {
            return config.getData().getBuy().entrySet().stream()
                    .filter(e -> currency.equals(e.getValue().getCurrency()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static String generateHmacSHA512Signature(String payload, String secretKey) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA512");
        hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return bytesToHex(hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    private String safeValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Double safeNumber(Object value) {
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
