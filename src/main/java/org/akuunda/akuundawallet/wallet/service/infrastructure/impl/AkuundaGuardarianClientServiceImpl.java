package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.transfert.impl.service.TransfertService;
import org.akuunda.akuundawallet.wallet.api.dao.GuadarianTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.MtPelerinTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.GuardarianTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaGuardarianClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AkuundaGuardarianClientServiceImpl implements AkuundaGuardarianClientService {

    public static final String GUADARIAN_OFFRAMP = "GUADARIAN_OFFRAMP";
    public static final String GUADARIAN = "GUADARIAN";
    public static final String YELLOWCARD = "YELLOWCARD";
    public static final String MTPELERIN = "MTPELERIN";
    public static final String GUADARIAN_ONRAMP = "GUADARIAN_ONRAMP";
    public static final String GUADARIAN_SWAP = "GUADARIAN_SWAP";
    public static final String MATIC = "MATIC";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final GuadarianTransactionRepository guadarianTransactionRepository;
    private final OperationRepository operationRepository;
    private final TransfertService transfertService;
    private final CurrencyFreaksClientService currencyFreaksClientService;

    @Value("${guardarian.api.url}")
    private String apiUrl;

    @Value("${guardarian.api.key}")
    private String apiKey;

    @Value("${guardarian.deposit.token}")
    private String depositToken;

    // ----------------------------------------
    // Public API
    // ----------------------------------------

    @Override
    public ResponseEntity<StatusResponse> getStatus() {
        return sendSimpleGet("/status", StatusResponse.class);
    }

    @Override
    public ResponseEntity<List<CurrencyResponse>> getCurrencies() {
        try {
            HttpRequest request = createRequestBuilder("/currencies").GET().build();

            CurrencyResponse[] responseArray = sendRequest(request, CurrencyResponse[].class);
            List<CurrencyResponse> responseList = Arrays.asList(responseArray);

            log.info("Nombre de devises récupérées : {}", responseList.size());
            return ResponseEntity.ok(responseList);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des devises : {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<List<MarketResponse>> getMarketInfo() {
        // Essayer plusieurs variantes d'endpoints selon la documentation Guardarian
        String[] endpointsToTry = {
                "/market-info",      // Variante suggérée par la documentation
                "/markets-info",     // Autre variante possible
                "/markets"           // Endpoint original
        };

        for (String endpoint : endpointsToTry) {
            try {
                log.info("Tentative de récupération des informations de marché via: {}", endpoint);
                HttpRequest request = createRequestBuilder(endpoint).GET().build();

                MarketResponse[] responseArray = sendRequest(request, MarketResponse[].class);
                List<MarketResponse> responseList = Arrays.asList(responseArray);

                log.info("✅ Succès avec endpoint '{}' - Nombre d'entrées marché récupérées : {}",
                        endpoint, responseList.size());
                return ResponseEntity.ok(responseList);

            } catch (RuntimeException e) {
                // Si c'est une 404, essayer le prochain endpoint
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    log.warn("❌ Endpoint '{}' retourne 404, tentative avec le suivant...", endpoint);
                    continue;
                }
                // Pour les autres erreurs, loguer et continuer quand même
                log.warn("⚠️ Erreur avec endpoint '{}': {}, tentative avec le suivant...",
                        endpoint, e.getMessage());
            } catch (Exception e) {
                log.warn("⚠️ Exception avec endpoint '{}': {}, tentative avec le suivant...",
                        endpoint, e.getMessage());
            }
        }

        // Si tous les endpoints ont échoué
        log.error("❌ Tous les endpoints de marché ont échoué. Endpoints testés: {}",
                Arrays.toString(endpointsToTry));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }


    @Override
    public ResponseEntity<List<PaymentCategoryResponse>> getPaymentCategories() {
        PaymentCategoryResponse[] arr = sendSimpleGet("/payment-categories", PaymentCategoryResponse[].class).getBody();
        return arr != null ? ResponseEntity.ok(Arrays.asList(arr))
                : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @Override
    public ResponseEntity<EstimateResponse> getEstimate(EstimateRequest estimateRequest) {
        Objects.requireNonNull(estimateRequest, "EstimateRequest ne doit pas être null");
        if (estimateRequest.amount() <= 0) {
            log.warn("Montant invalide pour l'estimation: {}", estimateRequest.amount());
            return ResponseEntity.badRequest().build();
        }

        // Essayer plusieurs variantes d'endpoints selon la documentation Guardarian
        String[] endpointsToTry = {
            "/estimate",              // Endpoint original
            "/estimate-amount",      // Variante possible
            "/estimates",            // Autre variante possible
            "/quote",                // Endpoint quote possible
            "/pricing",              // Endpoint pricing possible
            "/calculate",            // Endpoint calculate possible
            "/fees",                 // Endpoint fees direct
            "/rates/estimate"        // Rates avec estimate
        };

        // Construire le body JSON avec les noms de champs attendus par Guardarian
        Map<String, Object> requestBodyMap = new LinkedHashMap<>();
        requestBodyMap.put("from_currency", estimateRequest.from());
        requestBodyMap.put("to_currency", estimateRequest.to());
        requestBodyMap.put("from_amount", estimateRequest.amount());
        
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(requestBodyMap);
            log.debug("Body JSON pour Guardarian: {}", jsonBody);
        } catch (Exception e) {
            log.error("Erreur lors de la sérialisation de EstimateRequest: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        for (String endpoint : endpointsToTry) {
            try {
                log.info("Tentative d'estimation via endpoint: {} (POST)", endpoint);
                
                // Essayer d'abord avec POST (méthode standard)
                HttpRequest request = createRequestBuilder(endpoint)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                log.debug("Envoi de la requête HTTP POST vers {}", request.uri());
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                log.info("HTTP Status: {} pour endpoint '{}' (POST)", response.statusCode(), endpoint);
                log.debug("HTTP Response Body: {}", response.body());
                
                // Si 400, logger le body pour comprendre l'erreur
                if (response.statusCode() == 400) {
                    log.warn("⚠️ Endpoint '{}' (POST) retourne 400 Bad Request. Body de l'erreur: {}", endpoint, response.body());
                }

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    // ⚠️ IMPORTANT : Parser manuellement pour extraire estimated_exchange_rate et value
                    // car Guardarian retourne ces champs comme strings ou dans une structure spécifique
                    try {
                        var jsonNode = objectMapper.readTree(response.body());
                        log.info("📋 Structure JSON détectée (POST): {}", jsonNode.toPrettyString());
                        
                        // Extraire les champs de base
                        String parsedFromCurrency = jsonNode.has("from_currency") ? jsonNode.get("from_currency").asText() 
                                : (jsonNode.has("from") ? jsonNode.get("from").asText() : estimateRequest.from());
                        String parsedToCurrency = jsonNode.has("to_currency") ? jsonNode.get("to_currency").asText() 
                                : (jsonNode.has("to") ? jsonNode.get("to").asText() : estimateRequest.to());
                        double parsedAmount = jsonNode.has("from_amount") ? jsonNode.get("from_amount").asDouble() 
                                : (jsonNode.has("amount") ? jsonNode.get("amount").asDouble() : estimateRequest.amount());
                        
                        // Extraire estimatedAmount depuis value (montant reçu en USDC)
                        double estimatedAmount = 0.0;
                        if (jsonNode.has("value")) {
                            var valueNode = jsonNode.get("value");
                            if (valueNode.isNumber()) {
                                estimatedAmount = valueNode.asDouble();
                            } else if (valueNode.isTextual()) {
                                try {
                                    estimatedAmount = Double.parseDouble(valueNode.asText());
                                } catch (NumberFormatException e) {
                                    log.warn("Impossible de parser value comme nombre: {}", valueNode.asText());
                                }
                            }
                            log.info("✅ Montant value extrait depuis Guardarian (POST): {}", estimatedAmount);
                        } else if (jsonNode.has("estimatedAmount")) {
                            var amountNode = jsonNode.get("estimatedAmount");
                            if (amountNode.isNumber()) {
                                estimatedAmount = amountNode.asDouble();
                            } else if (amountNode.isTextual()) {
                                try {
                                    estimatedAmount = Double.parseDouble(amountNode.asText());
                                } catch (NumberFormatException e) {
                                    log.warn("Impossible de parser estimatedAmount comme nombre: {}", amountNode.asText());
                                }
                            }
                        } else if (jsonNode.has("estimated_amount")) {
                            var amountNode = jsonNode.get("estimated_amount");
                            if (amountNode.isNumber()) {
                                estimatedAmount = amountNode.asDouble();
                            } else if (amountNode.isTextual()) {
                                try {
                                    estimatedAmount = Double.parseDouble(amountNode.asText());
                                } catch (NumberFormatException e) {
                                    log.warn("Impossible de parser estimated_amount comme nombre: {}", amountNode.asText());
                                }
                            }
                        }
                        
                        // Extraire convertedAmount depuis converted_amount.amount (montant après frais dans la devise d'origine)
                        // ⚠️ IMPORTANT : Vérifier que converted_amount.currency correspond à la devise d'origine (from_currency)
                        Double convertedAmount = null;
                        if (jsonNode.has("converted_amount")) {
                            var convertedAmountNode = jsonNode.get("converted_amount");
                            if (convertedAmountNode.isObject() && convertedAmountNode.has("amount")) {
                                // Vérifier la devise si disponible
                                String convertedCurrency = null;
                                if (convertedAmountNode.has("currency")) {
                                    convertedCurrency = convertedAmountNode.get("currency").asText();
                                    log.info("🔍 Devise converted_amount.currency détectée: {}, devise d'origine (from_currency): {}", 
                                            convertedCurrency, parsedFromCurrency);
                                    
                                    // Si la devise ne correspond pas, ne pas utiliser convertedAmount
                                    if (!convertedCurrency.equalsIgnoreCase(parsedFromCurrency)) {
                                        log.warn("⚠️ Devise converted_amount.currency ({}) ne correspond pas à la devise d'origine ({}) - convertedAmount ignoré", 
                                                convertedCurrency, parsedFromCurrency);
                                    }
                                }
                                
                                var amountNode = convertedAmountNode.get("amount");
                                if (amountNode.isNumber()) {
                                    convertedAmount = amountNode.asDouble();
                                } else if (amountNode.isTextual()) {
                                    try {
                                        convertedAmount = Double.parseDouble(amountNode.asText());
                                    } catch (NumberFormatException e) {
                                        log.warn("Impossible de parser converted_amount.amount comme nombre: {}", amountNode.asText());
                                    }
                                }
                                
                                // ⚠️ IMPORTANT : Ne pas ignorer convertedAmount même si la devise ne correspond pas
                                // Guardarian peut retourner converted_amount.currency = "USDC" au lieu de la devise d'origine
                                // Mais converted_amount.amount est toujours dans la devise d'origine (from_currency)
                                // On utilisera convertedAmount s'il est disponible, la vérification de devise se fera dans FeesCalculationServiceImpl
                                if (convertedCurrency != null && !convertedCurrency.equalsIgnoreCase(parsedFromCurrency)) {
                                    log.warn("⚠️ Attention: converted_amount.currency ({}) ne correspond pas à la devise d'origine ({}), mais convertedAmount sera quand même utilisé", 
                                            convertedCurrency, parsedFromCurrency);
                                    // Ne pas mettre convertedAmount à null - on l'utilisera quand même
                                }
                            } else if (convertedAmountNode.isNumber()) {
                                convertedAmount = convertedAmountNode.asDouble();
                            } else if (convertedAmountNode.isTextual()) {
                                try {
                                    convertedAmount = Double.parseDouble(convertedAmountNode.asText());
                                } catch (NumberFormatException e) {
                                    log.warn("Impossible de parser converted_amount comme nombre: {}", convertedAmountNode.asText());
                                }
                            }
                            if (convertedAmount != null) {
                                log.info("✅ Montant converted_amount.amount extrait depuis Guardarian (POST): {} (devise: {})", 
                                        convertedAmount, parsedFromCurrency);
                            } else {
                                log.warn("⚠️ converted_amount.amount non trouvé, null, ou devise incorrecte dans la réponse Guardarian (POST)");
                            }
                        } else {
                            log.warn("⚠️ Champ converted_amount non trouvé dans la réponse Guardarian (POST)");
                        }
                        
                        // Extraire le rate (estimated_exchange_rate)
                        double rate = 0.0;
                        if (jsonNode.has("estimated_exchange_rate")) {
                            var rateNode = jsonNode.get("estimated_exchange_rate");
                            if (rateNode.isNumber()) {
                                rate = rateNode.asDouble();
                            } else if (rateNode.isTextual()) {
                                try {
                                    rate = Double.parseDouble(rateNode.asText());
                                } catch (NumberFormatException e) {
                                    log.warn("Impossible de parser estimated_exchange_rate comme nombre: {}", rateNode.asText());
                                }
                            }
                            log.info("✅ Taux estimated_exchange_rate extrait depuis Guardarian (POST): {}", rate);
                        } else if (jsonNode.has("rate")) {
                            var rateNode = jsonNode.get("rate");
                            if (rateNode.isNumber()) {
                                rate = rateNode.asDouble();
                            } else if (rateNode.isTextual()) {
                                try {
                                    rate = Double.parseDouble(rateNode.asText());
                                } catch (NumberFormatException e) {
                                    log.warn("Impossible de parser rate comme nombre: {}", rateNode.asText());
                                }
                            }
                        } else if (jsonNode.has("exchange_rate")) {
                            var rateNode = jsonNode.get("exchange_rate");
                            if (rateNode.isNumber()) {
                                rate = rateNode.asDouble();
                            } else if (rateNode.isTextual()) {
                                try {
                                    rate = Double.parseDouble(rateNode.asText());
                                } catch (NumberFormatException e) {
                                    log.warn("Impossible de parser exchange_rate comme nombre: {}", rateNode.asText());
                                }
                            }
                        } else {
                            log.warn("⚠️ Aucun champ de taux trouvé dans la réponse Guardarian (POST) (estimated_exchange_rate, rate, exchange_rate)");
                        }
                        
                        // Créer l'EstimateResponse avec les valeurs extraites
                        EstimateResponse estimateResponse = new EstimateResponse(
                                parsedFromCurrency,
                                parsedToCurrency,
                                parsedAmount,
                                estimatedAmount,
                                rate,
                                convertedAmount
                        );
                        
                        log.info("✅ Succès avec endpoint '{}' (POST). EstimateResponse créé: from={}, to={}, amount={}, estimatedAmount={}, rate={}", 
                                endpoint, estimateResponse.from(), estimateResponse.to(), estimateResponse.amount(), 
                                estimateResponse.estimatedAmount(), estimateResponse.rate());
                        return ResponseEntity.ok(estimateResponse);
                    } catch (Exception parseException) {
                        // Si le parsing détaillé échoue, essayer le parsing standard
                        log.warn("⚠️ Parsing détaillé échoué, tentative avec parsing standard: {}", parseException.getMessage());
                        try {
                            EstimateResponse estimateResponse = objectMapper.readValue(response.body(), EstimateResponse.class);
                            log.info("✅ Succès avec endpoint '{}' (POST, parsing standard)", endpoint);
                            return ResponseEntity.ok(estimateResponse);
                        } catch (Exception e) {
                            log.error("❌ Erreur lors du parsing de la réponse Guardarian (POST): {}. Réponse brute: {}", 
                                    e.getMessage(), response.body(), e);
                            continue; // Continuer avec le prochain endpoint
                        }
                    }
                } else if (response.statusCode() == 404) {
                    log.warn("❌ Endpoint '{}' (POST) retourne 404, tentative avec GET...", endpoint);
                    
                    // Essayer avec GET si POST retourne 404
                    try {
                        // Construire les paramètres de requête pour GET
                        // Essayer différents formats de paramètres
                        String fromCurrency = estimateRequest.from();
                        String toCurrency = estimateRequest.to();
                        double amount = estimateRequest.amount();
                        
                        // Essayer d'abord avec from_currency/to_currency/from_amount
                        String queryParams1 = String.format("from_currency=%s&to_currency=%s&from_amount=%s", 
                                fromCurrency, toCurrency, amount);
                        String urlWithParams1 = endpoint + "?" + queryParams1;
                        
                        HttpRequest getRequest1 = createRequestBuilder(urlWithParams1).GET().build();
                        log.debug("Envoi de la requête HTTP GET vers {} (format from_currency/to_currency)", getRequest1.uri());
                        HttpResponse<String> getResponse1 = httpClient.send(getRequest1, HttpResponse.BodyHandlers.ofString());
                        
                        log.info("HTTP Status: {} pour endpoint '{}' (GET, format from_currency)", getResponse1.statusCode(), endpoint);
                        log.debug("HTTP Response Body complet: {}", getResponse1.body());
                        
                        // Si 400, logger le body pour comprendre l'erreur
                        if (getResponse1.statusCode() == 400) {
                            log.warn("⚠️ Endpoint '{}' (GET, format from_currency) retourne 400 Bad Request. Body de l'erreur: {}", endpoint, getResponse1.body());
                        }
                        
                        if (getResponse1.statusCode() >= 200 && getResponse1.statusCode() < 300) {
                            // Logger la réponse brute pour comprendre la structure
                            log.info("📋 Réponse brute Guardarian (GET): {}", getResponse1.body());
                            
                            // Essayer de parser la réponse
                            try {
                                // Parser comme JsonNode pour voir la structure exacte
                                var jsonNode = objectMapper.readTree(getResponse1.body());
                                log.info("📋 Structure JSON détectée: {}", jsonNode.toPrettyString());
                                
                                // Essayer d'extraire les champs selon la structure Guardarian
                                // Structure possible 1: {converted_amount: {amount: "29.85", currency: "EUR"}, service_fees: [...]}
                                // Structure possible 2: {from: "EUR", to: "USDC", amount: 30.0, estimatedAmount: 29.85, rate: 1.125}
                                
                                // Utiliser les valeurs de la réponse JSON si disponibles, sinon utiliser les valeurs de la requête
                                String parsedFromCurrency = jsonNode.has("from_currency") ? jsonNode.get("from_currency").asText() 
                                        : (jsonNode.has("from") ? jsonNode.get("from").asText() : fromCurrency);
                                String parsedToCurrency = jsonNode.has("to_currency") ? jsonNode.get("to_currency").asText() 
                                        : (jsonNode.has("to") ? jsonNode.get("to").asText() : toCurrency);
                                double parsedAmount = jsonNode.has("from_amount") ? jsonNode.get("from_amount").asDouble() 
                                        : (jsonNode.has("amount") ? jsonNode.get("amount").asDouble() : amount);
                                
                                // Extraire estimatedAmount depuis value (montant reçu en USDC)
                                // ⚠️ IMPORTANT : Guardarian retourne "value" qui est le montant reçu en USDC
                                double estimatedAmount = 0.0;
                                if (jsonNode.has("value")) {
                                    // Guardarian retourne "value" comme string ou number
                                    var valueNode = jsonNode.get("value");
                                    if (valueNode.isNumber()) {
                                        estimatedAmount = valueNode.asDouble();
                                    } else if (valueNode.isTextual()) {
                                        try {
                                            estimatedAmount = Double.parseDouble(valueNode.asText());
                                        } catch (NumberFormatException e) {
                                            log.warn("Impossible de parser value comme nombre: {}", valueNode.asText());
                                        }
                                    }
                                    log.info("✅ Montant value extrait depuis Guardarian: {}", estimatedAmount);
                                } else if (jsonNode.has("estimatedAmount")) {
                                    var amountNode = jsonNode.get("estimatedAmount");
                                    if (amountNode.isNumber()) {
                                        estimatedAmount = amountNode.asDouble();
                                    } else if (amountNode.isTextual()) {
                                        try {
                                            estimatedAmount = Double.parseDouble(amountNode.asText());
                                        } catch (NumberFormatException e) {
                                            log.warn("Impossible de parser estimatedAmount comme nombre: {}", amountNode.asText());
                                        }
                                    }
                                } else if (jsonNode.has("estimated_amount")) {
                                    var amountNode = jsonNode.get("estimated_amount");
                                    if (amountNode.isNumber()) {
                                        estimatedAmount = amountNode.asDouble();
                                    } else if (amountNode.isTextual()) {
                                        try {
                                            estimatedAmount = Double.parseDouble(amountNode.asText());
                                        } catch (NumberFormatException e) {
                                            log.warn("Impossible de parser estimated_amount comme nombre: {}", amountNode.asText());
                                        }
                                    }
                                }
                                
                                // Extraire convertedAmount depuis converted_amount.amount (montant après frais dans la devise d'origine)
                                // ⚠️ IMPORTANT : Vérifier que converted_amount.currency correspond à la devise d'origine (from_currency)
                                Double convertedAmount = null;
                                if (jsonNode.has("converted_amount")) {
                                    var convertedAmountNode = jsonNode.get("converted_amount");
                                    if (convertedAmountNode.isObject() && convertedAmountNode.has("amount")) {
                                        // Vérifier la devise si disponible
                                        String convertedCurrency = null;
                                        if (convertedAmountNode.has("currency")) {
                                            convertedCurrency = convertedAmountNode.get("currency").asText();
                                            log.info("🔍 Devise converted_amount.currency détectée (GET): {}, devise d'origine (from_currency): {}", 
                                                    convertedCurrency, parsedFromCurrency);
                                            
                                            // Si la devise ne correspond pas, ne pas utiliser convertedAmount
                                            if (!convertedCurrency.equalsIgnoreCase(parsedFromCurrency)) {
                                                log.warn("⚠️ Devise converted_amount.currency ({}) ne correspond pas à la devise d'origine ({}) - convertedAmount ignoré", 
                                                        convertedCurrency, parsedFromCurrency);
                                            }
                                        }
                                        
                                        var amountNode = convertedAmountNode.get("amount");
                                        if (amountNode.isNumber()) {
                                            convertedAmount = amountNode.asDouble();
                                        } else if (amountNode.isTextual()) {
                                            try {
                                                convertedAmount = Double.parseDouble(amountNode.asText());
                                            } catch (NumberFormatException e) {
                                                log.warn("Impossible de parser converted_amount.amount comme nombre: {}", amountNode.asText());
                                            }
                                        }
                                        
                                        // ⚠️ IMPORTANT : Ne pas ignorer convertedAmount même si la devise ne correspond pas
                                        // Guardarian peut retourner converted_amount.currency = "USDC" au lieu de la devise d'origine
                                        // Mais converted_amount.amount est toujours dans la devise d'origine (from_currency)
                                        // On utilisera convertedAmount s'il est disponible, la vérification de devise se fera dans FeesCalculationServiceImpl
                                        if (convertedCurrency != null && !convertedCurrency.equalsIgnoreCase(parsedFromCurrency)) {
                                            log.warn("⚠️ Attention: converted_amount.currency ({}) ne correspond pas à la devise d'origine ({}), mais convertedAmount sera quand même utilisé", 
                                                    convertedCurrency, parsedFromCurrency);
                                            // Ne pas mettre convertedAmount à null - on l'utilisera quand même
                                        }
                                    } else if (convertedAmountNode.isNumber()) {
                                        convertedAmount = convertedAmountNode.asDouble();
                                    } else if (convertedAmountNode.isTextual()) {
                                        try {
                                            convertedAmount = Double.parseDouble(convertedAmountNode.asText());
                                        } catch (NumberFormatException e) {
                                            log.warn("Impossible de parser converted_amount comme nombre: {}", convertedAmountNode.asText());
                                        }
                                    }
                                    if (convertedAmount != null) {
                                        log.info("✅ Montant converted_amount.amount extrait depuis Guardarian (GET): {} (devise: {})", 
                                                convertedAmount, parsedFromCurrency);
                                    } else {
                                        log.warn("⚠️ converted_amount.amount non trouvé, null, ou devise incorrecte dans la réponse Guardarian (GET)");
                                    }
                                }
                                
                                // Essayer d'extraire le rate
                                // ⚠️ IMPORTANT : Guardarian retourne estimated_exchange_rate comme string ou number
                                double rate = 0.0;
                                if (jsonNode.has("estimated_exchange_rate")) {
                                    var rateNode = jsonNode.get("estimated_exchange_rate");
                                    if (rateNode.isNumber()) {
                                        rate = rateNode.asDouble();
                                    } else if (rateNode.isTextual()) {
                                        // Si c'est une string, la parser
                                        try {
                                            rate = Double.parseDouble(rateNode.asText());
                                        } catch (NumberFormatException e) {
                                            log.warn("Impossible de parser estimated_exchange_rate comme nombre: {}", rateNode.asText());
                                        }
                                    }
                                    log.info("✅ Taux estimated_exchange_rate extrait depuis Guardarian: {}", rate);
                                } else if (jsonNode.has("rate")) {
                                    var rateNode = jsonNode.get("rate");
                                    if (rateNode.isNumber()) {
                                        rate = rateNode.asDouble();
                                    } else if (rateNode.isTextual()) {
                                        try {
                                            rate = Double.parseDouble(rateNode.asText());
                                        } catch (NumberFormatException e) {
                                            log.warn("Impossible de parser rate comme nombre: {}", rateNode.asText());
                                        }
                                    }
                                } else if (jsonNode.has("exchange_rate")) {
                                    var rateNode = jsonNode.get("exchange_rate");
                                    if (rateNode.isNumber()) {
                                        rate = rateNode.asDouble();
                                    } else if (rateNode.isTextual()) {
                                        try {
                                            rate = Double.parseDouble(rateNode.asText());
                                        } catch (NumberFormatException e) {
                                            log.warn("Impossible de parser exchange_rate comme nombre: {}", rateNode.asText());
                                        }
                                    }
                                } else {
                                    log.warn("⚠️ Aucun champ de taux trouvé dans la réponse Guardarian (estimated_exchange_rate, rate, exchange_rate)");
                                }
                                
                                // Créer l'EstimateResponse avec les valeurs extraites
                                EstimateResponse estimateResponse = new EstimateResponse(
                                        parsedFromCurrency,
                                        parsedToCurrency,
                                        parsedAmount,
                                        estimatedAmount,
                                        rate,
                                        convertedAmount
                                );
                                
                                log.info("✅ Succès avec endpoint '{}' (GET, format from_currency). EstimateResponse créé: from={}, to={}, amount={}, estimatedAmount={}, rate={}, convertedAmount={}", 
                                        endpoint, estimateResponse.from(), estimateResponse.to(), estimateResponse.amount(), 
                                        estimateResponse.estimatedAmount(), estimateResponse.rate(), estimateResponse.convertedAmount());
                                return ResponseEntity.ok(estimateResponse);
                            } catch (Exception parseException) {
                                // Si le parsing échoue, logger l'erreur
                                log.error("❌ Erreur lors du parsing de la réponse Guardarian: {}. Réponse brute: {}", 
                                        parseException.getMessage(), getResponse1.body(), parseException);
                                // Ne pas continuer ici, on va essayer le format suivant
                            }
                        }
                        
                        // Si ça ne fonctionne pas, essayer avec from/to/from_amount
                        String queryParams2 = String.format("from=%s&to=%s&from_amount=%s", 
                                fromCurrency, toCurrency, amount);
                        String urlWithParams2 = endpoint + "?" + queryParams2;
                        
                        HttpRequest getRequest2 = createRequestBuilder(urlWithParams2).GET().build();
                        log.debug("Envoi de la requête HTTP GET vers {} (format from/to)", getRequest2.uri());
                        HttpResponse<String> getResponse2 = httpClient.send(getRequest2, HttpResponse.BodyHandlers.ofString());
                        
                        log.info("HTTP Status: {} pour endpoint '{}' (GET, format from/to)", getResponse2.statusCode(), endpoint);
                        log.debug("HTTP Response Body: {}", getResponse2.body());
                        
                        // Si 400, logger le body pour comprendre l'erreur
                        if (getResponse2.statusCode() == 400) {
                            log.warn("⚠️ Endpoint '{}' (GET, format from/to) retourne 400 Bad Request. Body de l'erreur: {}", endpoint, getResponse2.body());
                        }
                        
                        if (getResponse2.statusCode() >= 200 && getResponse2.statusCode() < 300) {
                            EstimateResponse estimateResponse = objectMapper.readValue(getResponse2.body(), EstimateResponse.class);
                            log.info("✅ Succès avec endpoint '{}' (GET, format from/to)", endpoint);
                            return ResponseEntity.ok(estimateResponse);
                        }
                        
                        // Si les deux formats GET échouent, continuer avec le prochain endpoint
                        if (getResponse1.statusCode() == 404 && getResponse2.statusCode() == 404) {
                            log.warn("❌ Endpoint '{}' (GET) retourne 404 avec les deux formats, tentative avec le suivant...", endpoint);
                            continue;
                        } else {
                            log.warn("⚠️ Endpoint '{}' (GET) retourne des erreurs, tentative avec le suivant...", endpoint);
                            continue;
                        }
                    } catch (Exception getException) {
                        log.warn("⚠️ Exception avec endpoint '{}' (GET): {}, tentative avec le suivant...", 
                                endpoint, getException.getMessage());
                        continue;
                    }
                } else {
                    log.error("Erreur API Guardarian - Code: {}, Body: {} pour endpoint '{}'", 
                            response.statusCode(), response.body(), endpoint);
                    // Pour les autres erreurs (401, 403, 500, etc.), retourner le status code
                    return ResponseEntity.status(response.statusCode()).build();
                }
            } catch (Exception e) {
                log.warn("⚠️ Exception avec endpoint '{}': {}, tentative avec le suivant...", 
                        endpoint, e.getMessage());
                // Continuer avec le prochain endpoint
            }
        }

        // Si tous les endpoints ont échoué
        log.error("❌ Tous les endpoints d'estimation ont échoué. Endpoints testés: {}", 
                Arrays.toString(endpointsToTry));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @Override
    public ResponseEntity<GuadarianResponse> withdraw(@Valid TransactionRequest request) {
        // Convertir le montant de la devise fiat (ex: EUR) en USDC via CurrencyFreaks
        // L'utilisateur saisit en devise locale (ex: 46 EUR), Guardarian attend du USDC
        String toCurrency = request.to_currency(); // ex: "EUR"

        if (toCurrency != null && !toCurrency.equalsIgnoreCase("USDC") && !toCurrency.equalsIgnoreCase("USD")) {
            try {
                log.info("🔄 Conversion du montant off-ramp: {} {} → USDC via CurrencyFreaks", request.from_amount(), toCurrency);

                ResponseEntity<CurrencyConversionResponse> conversionResponse =
                    currencyFreaksClientService.convertCurrency(toCurrency, "USDC", request.from_amount());

                if (conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
                    double convertedAmount = Double.parseDouble(conversionResponse.getBody().getConvertedAmount());
                    log.info("✅ Montant converti: {} {} → {} USDC (taux: {})",
                        request.from_amount(), toCurrency, convertedAmount, conversionResponse.getBody().getRate());

                    // Reconstruire le TransactionRequest avec le montant converti en USDC
                    request = TransactionRequest.builder()
                        .from_currency(request.from_currency())
                        .to_currency(request.to_currency())
                        .email(request.email())
                        .username(request.username())
                        .from_amount(convertedAmount)
                        .from_network(request.from_network())
                        .to_network(request.to_network())
                        .build();
                } else {
                    log.error("❌ Échec de la conversion CurrencyFreaks: {}", conversionResponse.getStatusCode());
                    return new ResponseEntity<>(
                        GuadarianResponse.builder()
                            .status("error")
                            .message("Impossible de convertir le montant en USDC")
                            .build(),
                        HttpStatus.BAD_REQUEST);
                }
            } catch (Exception e) {
                log.error("❌ Erreur lors de la conversion du montant: {}", e.getMessage(), e);
                return new ResponseEntity<>(
                    GuadarianResponse.builder()
                        .status("error")
                        .message("Erreur lors de la conversion du montant: " + e.getMessage())
                        .build(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // Off-ramp = retrait → fonds sortants → DEBIT côté wallet utilisateur.
        // (Legacy : ce code passait "CREDIT", ce qui faisait apparaître les retraits
        // en vert sur l'historique marchand.)
        return handleTransaction(request, GUADARIAN_OFFRAMP, "DEBIT");
    }

    @Override
    public ResponseEntity<GuadarianResponse> deposit(@Valid TransactionRequest request) {
        // On-ramp = rechargement → fonds entrants → CREDIT côté wallet utilisateur.
        return handleTransaction(request, GUADARIAN_ONRAMP, "CREDIT");
    }

    @Override
    public ResponseEntity<TransactionStatusResponse> getTransactionById(String transactionId, String username) {

        // Vérifier que le username est fourni
        if (username == null || username.isBlank()) {
            log.error("Username manquant pour la récupération de la transaction Guardarian ID: {}", transactionId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Vérifier que l'opération existe et appartient à l'utilisateur
        Operation operation = operationRepository.findByOperationHash(transactionId);
        if (operation == null) {
            log.warn("Aucune opération trouvée pour la transaction Guardarian ID: {}", transactionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Vérifier que l'opération appartient à l'utilisateur
        if (!operation.getUsername().equals(username)) {
            log.warn("Tentative d'accès non autorisée : transaction {} demandée par {} mais appartient à {}", 
                    transactionId, username, operation.getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (transactionId == null) return ResponseEntity.badRequest().build();
        try {
            TransactionDetailResponse response = sendRequest(createRequestBuilder("/transaction/" + transactionId).GET().build(), TransactionDetailResponse.class);
            if (response == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Vérifier que la transaction Guardarian appartient à l'utilisateur
            GuardarianTransaction tx = guadarianTransactionRepository.findByExternalTransactionIdAndUsername(
                    Long.valueOf(response.getId()), username)
                    .orElse(null);

            // Si la transaction n'existe pas en base, la créer mais seulement si elle appartient à l'utilisateur
            if (tx == null) {
                // Vérifier dans la réponse de Guardarian que external_partner_link_id correspond au username
                if (response.getExternalPartnerLinkId() != null && !response.getExternalPartnerLinkId().equals(username)) {
                    log.warn("Tentative d'accès non autorisée : transaction Guardarian {} demandée par {} mais external_partner_link_id est {}", 
                            response.getId(), username, response.getExternalPartnerLinkId());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                
                tx = new GuardarianTransaction();
                tx.setExternalTransactionId(Long.valueOf(response.getId()));
                tx.setUsername(username);
                tx.setCreatedAt(LocalDateTime.now());
            } else {
                // Vérifier que la transaction en base appartient bien à l'utilisateur
                if (!username.equals(tx.getUsername())) {
                    log.warn("Tentative d'accès non autorisée : transaction Guardarian {} demandée par {} mais appartient à {}", 
                            response.getId(), username, tx.getUsername());
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }

            // mise à jour des champs
            updateTransactionFields(tx, response);
            guadarianTransactionRepository.saveAndFlush(tx);

            operation.setStatus(tx.getStatus());
            operation.setUpdatedAt(LocalDateTime.now());
            operationRepository.saveAndFlush(operation);

            TransactionStatusResponse transactionStatusResponse = new TransactionStatusResponse(
                    tx.getStatus(),
                    tx.getUsername(),
                    tx.getExternalTransactionId() + ""
            );
            return new ResponseEntity<>(transactionStatusResponse, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Erreur getTransactionById {} : {}", transactionId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<SimpleTransactionResponse> getSimpleTransactionById(String transactionId, String username) {
        // Utiliser la méthode existante pour récupérer la transaction
        ResponseEntity<TransactionStatusResponse> response = getTransactionById(transactionId, username);
        
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            return ResponseEntity.status(response.getStatusCode()).build();
        }
        
        // Récupérer la transaction complète depuis la base de données
        Operation operation = operationRepository.findByOperationHash(transactionId);
        if (operation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        GuardarianTransaction tx = guadarianTransactionRepository.findByExternalTransactionIdAndUsername(
                Long.valueOf(transactionId), username).orElse(null);
        
        if (tx == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        // Mapper vers SimpleTransactionResponse
        SimpleTransactionResponse simpleResponse = mapGuardarianToSimple(tx, transactionId);
        return ResponseEntity.ok(simpleResponse);
    }

    /**
     * Mappe une transaction Guardarian depuis l'API vers le format simplifié
     * ⚠️ IMPORTANT : Utilise les données directement depuis l'API Guardarian
     */
    private SimpleTransactionResponse mapGuardarianToSimpleFromAPI(TransactionDetailResponse response, String transactionId) {
        // Utiliser l'ID réel : externalTransactionId (transactionId)
        String id = transactionId;
        
        // Mapper le statut
        String status = mapGuardarianStatus(response.getStatus());
        
        // Extraire la date depuis l'API
        Instant date = response.getCreatedAt() != null 
                ? response.getCreatedAt().toInstant()
                : (response.getUpdatedAt() != null 
                        ? response.getUpdatedAt().toInstant()
                        : Instant.now());
        
        // Déterminer le type de transaction (ONRAMP ou OFFRAMP)
        String type = determineGuardarianTransactionType(response.getFromCurrency(), response.getToCurrency());
        
        // Utiliser la currency fiat (toCurrency si c'est fiat, sinon fromCurrency si c'est fiat)
        String currency = null;
        Double amount = null;
        
        // Si toCurrency est fiat → utiliser toCurrency et toAmount
        if (response.getToCurrency() != null && !isCryptoCurrency(response.getToCurrency())) {
            currency = response.getToCurrency();
            amount = response.getToAmount();
        }
        // Sinon, si fromCurrency est fiat → utiliser fromCurrency et fromAmount
        else if (response.getFromCurrency() != null && !isCryptoCurrency(response.getFromCurrency())) {
            currency = response.getFromCurrency();
            amount = response.getFromAmount();
        }
        
        if (amount == null || currency == null) {
            log.warn("Montant ou devise fiat manquant pour la transaction Guardarian: {} (from: {}, to: {})", 
                    transactionId, response.getFromCurrency(), response.getToCurrency());
            return null;
        }
        
        // Utiliser directement la valeur avec les décimales (pas de conversion)
        // Le champ amount dans SimpleTransactionResponse est maintenant Double pour garder les décimales
        
        return SimpleTransactionResponse.builder()
                .id(id)
                .status(status)
                .date(date)
                .amount(amount)
                .currency(currency)
                .operator("GUARDIARAN")
                .type(type)
                .build();
    }
    
    /**
     * Mappe une transaction Guardarian depuis la base de données vers le format simplifié
     * (fallback si l'API n'est pas disponible)
     */
    private SimpleTransactionResponse mapGuardarianToSimple(GuardarianTransaction tx, String transactionId) {
        // Utiliser l'ID réel : externalTransactionId (transactionId)
        String id = transactionId;
        
        // Mapper le statut
        String status = mapGuardarianStatus(tx.getStatus());
        
        // Extraire la date
        Instant date = tx.getCreatedAt() != null 
                ? tx.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : (tx.getUpdatedAt() != null 
                        ? tx.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : Instant.now());
        
        // Déterminer le type de transaction (ONRAMP ou OFFRAMP)
        String type = determineGuardarianTransactionType(tx.getFromCurrency(), tx.getToCurrency());
        
        // Utiliser la currency fiat (toCurrency si c'est fiat, sinon fromCurrency si c'est fiat)
        String currency = null;
        Double amount = null;
        
        // Si toCurrency est fiat → utiliser toCurrency et toAmount
        if (tx.getToCurrency() != null && !isCryptoCurrency(tx.getToCurrency())) {
            currency = tx.getToCurrency();
            amount = tx.getToAmount();
        }
        // Sinon, si fromCurrency est fiat → utiliser fromCurrency et fromAmount
        else if (tx.getFromCurrency() != null && !isCryptoCurrency(tx.getFromCurrency())) {
            currency = tx.getFromCurrency();
            amount = tx.getFromAmount();
        }
        
        if (amount == null || currency == null) {
            log.warn("Montant ou devise fiat manquant pour la transaction Guardarian: {} (from: {}, to: {})", 
                    transactionId, tx.getFromCurrency(), tx.getToCurrency());
            return null;
        }
        
        // Utiliser directement la valeur avec les décimales (pas de conversion)
        // Le champ amount dans SimpleTransactionResponse est maintenant Double pour garder les décimales
        
        return SimpleTransactionResponse.builder()
                .id(id)
                .status(status)
                .date(date)
                .amount(amount)
                .currency(currency)
                .operator("GUARDIARAN")
                .type(type)
                .build();
    }
    
    /**
     * Vérifie si une devise est une crypto-monnaie
     */
    private boolean isCryptoCurrency(String currency) {
        if (currency == null) {
            return false;
        }
        String upperCurrency = currency.toUpperCase();
        // Liste des cryptos courantes
        return upperCurrency.equals("USDC") || upperCurrency.equals("USDT") 
                || upperCurrency.equals("BTC") || upperCurrency.equals("ETH")
                || upperCurrency.equals("MATIC") || upperCurrency.equals("BNB")
                || upperCurrency.equals("SOL") || upperCurrency.equals("XRP")
                || upperCurrency.equals("ADA") || upperCurrency.equals("DOT")
                || upperCurrency.equals("DOGE") || upperCurrency.equals("LTC")
                || upperCurrency.equals("BCH") || upperCurrency.equals("TRX")
                || upperCurrency.equals("AVAX") || upperCurrency.equals("LINK")
                || upperCurrency.equals("UNI") || upperCurrency.equals("ATOM");
    }
    
    /**
     * Détermine le type de transaction Guardarian (ONRAMP ou OFFRAMP)
     * @param fromCurrency Devise source
     * @param toCurrency Devise destination
     * @return "ONRAMP" si from est fiat et to est crypto, "OFFRAMP" si from est crypto et to est fiat
     */
    private String determineGuardarianTransactionType(String fromCurrency, String toCurrency) {
        boolean fromIsFiat = fromCurrency != null && !isCryptoCurrency(fromCurrency);
        boolean toIsFiat = toCurrency != null && !isCryptoCurrency(toCurrency);
        
        // Si from est fiat et to est crypto → ONRAMP
        if (fromIsFiat && !toIsFiat) {
            return "ONRAMP";
        }
        
        // Si from est crypto et to est fiat → OFFRAMP
        if (!fromIsFiat && toIsFiat) {
            return "OFFRAMP";
        }
        
        // Par défaut si on ne peut pas déterminer
        return "UNKNOWN";
    }

    /**
     * Génère un ID simplifié au format AKU-YYYYMMDD-XXXX
     */
    private String generateSimpleTransactionId(GuardarianTransaction tx, String transactionId) {
        LocalDateTime date = tx.getCreatedAt() != null ? tx.getCreatedAt() : tx.getUpdatedAt();
        if (date == null) {
            date = LocalDateTime.now();
        }
        
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // Utiliser les 4 derniers chiffres de l'ID externe
        String uniqueId = transactionId.length() >= 4 
                ? transactionId.substring(transactionId.length() - 4)
                : String.format("%04d", Math.abs(transactionId.hashCode() % 10000));
        
        return String.format("AKU-%s-%s", dateStr, uniqueId);
    }

    /**
     * Mappe le statut Guardarian vers le format simplifié
     */
    private String mapGuardarianStatus(String guardarianStatus) {
        if (guardarianStatus == null) {
            return "pending";
        }
        return switch (guardarianStatus.toLowerCase()) {
            case "finished" -> "completed";
            case "pending" -> "pending";
            case "cancelled", "failed" -> "failed";
            default -> guardarianStatus.toLowerCase();
        };
    }

    /**
     * Convertit le montant en centimes pour XOF/XAF, garde en unités pour EUR
     */
    private Long convertToAmount(double amount, String currency) {
        if ("XOF".equalsIgnoreCase(currency) || "XAF".equalsIgnoreCase(currency)) {
            return Math.round(amount * 100);
        } else {
            return Math.round(amount);
        }
    }

    @Override
    public ResponseEntity<List<SimpleTransactionResponse>> getSimpleTransactions(
            String username,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Integer skip,
            Integer limit) {
        
        log.info("Récupération de l'historique Guardarian pour utilisateur: {} (from: {}, to: {}, skip: {}, limit: {})", 
                username, fromDate, toDate, skip, limit);
        
        // Vérifier que le username est fourni
        if (username == null || username.isBlank()) {
            log.error("Username manquant pour la récupération de l'historique Guardarian");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        try {
            // Récupérer les transactions Guardarian depuis la base de données filtrées par username
            List<GuardarianTransaction> allTransactions = guadarianTransactionRepository
                    .findByUsernameOrderByCreatedAtDesc(username);
            
            log.info("Trouvé {} transactions Guardarian pour l'utilisateur {}", allTransactions.size(), username);
            
            // Filtrer par dates si fournies
            List<GuardarianTransaction> filteredTransactions = allTransactions.stream()
                    .filter(tx -> {
                        if (fromDate != null && tx.getCreatedAt() != null && tx.getCreatedAt().isBefore(fromDate)) {
                            return false;
                        }
                        if (toDate != null && tx.getCreatedAt() != null && tx.getCreatedAt().isAfter(toDate)) {
                            return false;
                        }
                        return true;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            log.info("Après filtrage par dates: {} transactions", filteredTransactions.size());
            
            // Appliquer la pagination
            int skipValue = skip != null && skip >= 0 ? skip : 0;
            int limitValue = limit != null && limit > 0 ? limit : 100;
            
            List<GuardarianTransaction> paginatedTransactions = filteredTransactions.stream()
                    .skip(skipValue)
                    .limit(limitValue)
                    .collect(java.util.stream.Collectors.toList());
            
            // Mapper chaque transaction vers SimpleTransactionResponse
            // ⚠️ IMPORTANT : Récupérer les données depuis l'API Guardarian pour chaque transaction
            List<SimpleTransactionResponse> transactions = new ArrayList<>();
            for (GuardarianTransaction tx : paginatedTransactions) {
                try {
                    String transactionId = tx.getExternalTransactionId() != null 
                            ? tx.getExternalTransactionId().toString() 
                            : String.valueOf(tx.getId());
                    
                    // Récupérer les données fraîches depuis l'API Guardarian
                    try {
                        TransactionDetailResponse apiResponse = sendRequest(
                                createRequestBuilder("/transaction/" + transactionId).GET().build(), 
                                TransactionDetailResponse.class);
                        
                        if (apiResponse != null) {
                            // Vérifier que la transaction appartient à l'utilisateur
                            if (apiResponse.getExternalPartnerLinkId() != null && 
                                    !apiResponse.getExternalPartnerLinkId().equals(username)) {
                                log.warn("Transaction {} n'appartient pas à l'utilisateur {}", transactionId, username);
                                continue;
                            }
                            
                            // Utiliser les données de l'API pour mapper
                            SimpleTransactionResponse simpleTx = mapGuardarianToSimpleFromAPI(apiResponse, transactionId);
                            if (simpleTx != null) {
                                transactions.add(simpleTx);
                            }
                        } else {
                            // Fallback sur la base de données si l'API échoue
                            SimpleTransactionResponse simpleTx = mapGuardarianToSimple(tx, transactionId);
                            if (simpleTx != null) {
                                transactions.add(simpleTx);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Erreur lors de la récupération depuis l'API Guardarian pour transaction {}: {}", 
                                transactionId, e.getMessage());
                        // Fallback sur la base de données
                        SimpleTransactionResponse simpleTx = mapGuardarianToSimple(tx, transactionId);
                        if (simpleTx != null) {
                            transactions.add(simpleTx);
                        }
                    }
                } catch (Exception e) {
                    log.error("Erreur lors du mapping de la transaction Guardarian {}: {}", 
                            tx.getId(), e.getMessage(), e);
                }
            }
            
            log.info("Retour de {} transactions Guardarian pour l'utilisateur {}", transactions.size(), username);
            return ResponseEntity.ok(transactions);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'historique Guardarian pour utilisateur {}: {}", 
                    username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> detectTransactionType(String fromCurrency, String toCurrency) {
        try {
            List<Map<String, Object>> fiatList = Arrays.asList(
                    sendRequest(createRequestBuilder("/currencies/fiat?available=true")
                            .GET().build(), Map[].class));
            Set<String> fiatTickers = new HashSet<>();
            for (Map<String, Object> fiat : fiatList) {
                Object ticker = fiat.get("ticker");
                if (ticker != null) fiatTickers.add(ticker.toString().toUpperCase());
            }

            String type = resolveType(fromCurrency, toCurrency, fiatTickers);
            Map<String, Object> result = new HashMap<>();
            result.put("fromCurrency", fromCurrency.toUpperCase());
            result.put("toCurrency", toCurrency.toUpperCase());
            result.put("transactionType", type);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur detectTransactionType: {}", e.getMessage(), e);
            Map<String, Object> err = Map.of("error", "Erreur lors de la détection du type de transaction", "details", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @Override
    public ResponseEntity<GuadarianResponse> paiement(GuardiaranPaiement request) {

        log.debug("Paiement {}", request);
        log.info("Paiement {}", request);
        Objects.requireNonNull(request, "TransactionRequest required");
        if (request.walletAddress() == null || request.headerPin() == null
                || request.username() == null || request.transactionId() == null) {
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("error")
                    .message("Champs obligatoires manquants dans la requête")
                    .build();
            return new ResponseEntity<>(guadarianResponse, HttpStatus.BAD_REQUEST);
        }

       // GuardarianTransaction transaction = new GuardarianTransaction();
        // 1. verifier que le user existe
        Users users = userRepository.getUsersByUsername(request.username());
        if (users == null) {
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("error")
                    .message("Aucun utilisateur trouvé avec username : " + request.username())
                    .build();
            return new ResponseEntity<>(guadarianResponse, HttpStatus.NOT_FOUND);
        }

        // 2. recuperer le wallet de user
        Wallet wallet = walletRepository.findByUsers(users);
        if (wallet == null) {
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("error")
                    .message("Aucun Wallet trouvé pour utilisateur : " + request.username())
                    .build();
            return new ResponseEntity<>(guadarianResponse, HttpStatus.NOT_FOUND);
        }

        // 3. recuperer la transaction en fonction de l'id stocke dans le champ hash de operation

        Operation operation = operationRepository.findByOperationHash(request.transactionId());
        if (operation == null) {
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("error")
                    .message("Aucune opération trouvée avec id transaction : " + request.transactionId())
                    .build();
            return new ResponseEntity<>(guadarianResponse, HttpStatus.NOT_FOUND);
        }

        String walletAdress = request.walletAddress();

        // 5. appeller le service de transfert token
        ResponseEntity<String> transfertResponse = transfertService.executeOnRampTokenTransfert(users,
                walletAdress, request.amount(), request.amount(), request.headerPin(), operation.getDevise());

        if (transfertResponse.getStatusCode().is2xxSuccessful()) {
            log.info("Transfert réussi pour {}", request.transactionId());

            operation.setStatus("valide");
            operation.setConvertedAmount(request.amount());
            operation.setUpdatedAt(LocalDateTime.now());
            operationRepository.saveAndFlush(operation);

            // recuperer le nouveau statut de la transaction
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("success")
                    .message("Transfert effectué avec succés pour id transaction : " + request.transactionId())
                    .build();
            return new ResponseEntity<>(guadarianResponse, HttpStatus.OK);
        } else {
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("error")
                    .message(transfertResponse.getBody())
                    .build();
            return new ResponseEntity<>(guadarianResponse, transfertResponse.getStatusCode());
        }
    }

    // ----------------------------------------
    // Utilitaires internes
    // ----------------------------------------

    private <T> ResponseEntity<T> sendSimpleGet(String endpoint, Class<T> clazz) {
        try {
            T response = sendRequest(createRequestBuilder(endpoint).GET().build(), clazz);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur GET {} : {}", endpoint, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private <T> ResponseEntity<List<T>> sendFallbackGet(String[] endpoints, Class<T[]> clazz) {
        for (String endpoint : endpoints) {
            try {
                T[] arr = sendRequest(createRequestBuilder(endpoint).GET().build(), clazz);
                return ResponseEntity.ok(Arrays.asList(arr));
            } catch (Exception e) {
                log.warn("Fallback GET endpoint '{}' failed: {}", endpoint, e.getMessage());
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private ResponseEntity<EstimateResponse> sendEstimateWithFallback(String[] endpoints, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            for (String endpoint : endpoints) {
                try {
                    HttpRequest req = createRequestBuilder(endpoint)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        EstimateResponse est = objectMapper.readValue(resp.body(), EstimateResponse.class);
                        return ResponseEntity.ok(est);
                    }
                } catch (Exception ignored) {}
            }
        } catch (JsonProcessingException e) {
            log.error("Erreur sérialisation payload: {}", e.getMessage(), e);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    private ResponseEntity<GuadarianResponse> handleTransaction(TransactionRequest request, String designation, String type) {
        try {
            Objects.requireNonNull(request, "TransactionRequest required");
            if (request.from_amount() == null || request.from_amount() <= 0) {
                // en cas d'erreur le data est null
                GuadarianResponse guadarianResponse = GuadarianResponse
                        .builder()
                        .status("error")
                        .message("Montant invalide")
                        .build();
                return new ResponseEntity<>(guadarianResponse, HttpStatus.BAD_REQUEST);
            }

            Users user = userRepository.getUsersByUsername(request.username());
            if (user == null) {
                // en cas d'erreur le data est null
                GuadarianResponse guadarianResponse = GuadarianResponse
                        .builder()
                        .status("error")
                        .message("utilisateur inexistant")
                        .build();
                return new ResponseEntity<>(guadarianResponse, HttpStatus.NOT_FOUND);
            }

            Wallet wallet = walletRepository.findByUsers(user);
            if (wallet == null) {
                GuadarianData guadarianData = GuadarianData
                        .builder()
                        .build();
                GuadarianResponse guadarianResponse = GuadarianResponse
                        .builder()
                        .status("error")
                        .message("Wallet de l'utilisateur inexistant")
                        .data(guadarianData)
                        .build();
                return new ResponseEntity<>(guadarianResponse, HttpStatus.NOT_FOUND);
            }

            boolean isOffRamp;

            isOffRamp = designation.equals(GUADARIAN_OFFRAMP); // retrait

            String json = buildTransactionPayload(request, wallet, isOffRamp);
            HttpRequest httpRequest = createRequestBuilder("/transaction")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201 && response.statusCode() != 200) {
                // en cas d'erreur le data est null
                GuadarianResponse guadarianResponse = GuadarianResponse
                        .builder()
                        .status("error")
                        .message(response.body())
                        .build();
                return new ResponseEntity<>(guadarianResponse, HttpStatus.valueOf(response.statusCode()));
            }

            TransactionResponse tr = objectMapper.readValue(response.body(), TransactionResponse.class);

            GuardarianTransaction tx = buildGuardarianTransaction(tr, request);
            guadarianTransactionRepository.save(tx);

            Operation op = buildOperation(tx, designation, type);
            operationRepository.save(op);

            GuadarianData guadarianData = GuadarianData
                    .builder()
                    .id(tx.getId())
                    .redirect_url(tr.redirect_url())
                    .username(request.username())
                    .build();
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("success")
                    .message("Operation effectuée avec succès")
                    .data(guadarianData)
                    .build();
            return new ResponseEntity<>(guadarianResponse, HttpStatus.OK);

        } catch (Exception e) {
            log.error("{} transaction error: {}", designation, e.getMessage(), e);
            // en cas d'erreur le data est null
            GuadarianResponse guadarianResponse = GuadarianResponse
                    .builder()
                    .status("error")
                    .message(e.getMessage())
                    .build();
            return new ResponseEntity<>(guadarianResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private GuardarianTransaction buildGuardarianTransaction(TransactionResponse tr, TransactionRequest req) {
        String payoutAddress = null;
        String payoutExtraId = null;

        if (tr.payout() != null) {
            payoutAddress = tr.payout().get("address");
            payoutExtraId = tr.payout().get("extra_id");
        }

        return GuardarianTransaction.builder()
                // --- Identifiants ---
                .externalTransactionId(tr.id())
                .partnerId(tr.partner_id())
                .externalPartnerLinkId(tr.external_partner_link_id())

                // --- Client ---
                .email(tr.email())
                .username(req.username())

                // --- Statuts ---
                .status(tr.status())
                .statusDetails(tr.status_details())

                // --- Monétaires & réseaux ---
                .fromCurrency(tr.from_currency())
                .toCurrency(tr.to_currency())
                .fromNetwork(tr.from_network())
                .toNetwork(tr.to_network())
                .fromCurrencyWithNetwork(tr.from_currency_with_network())
                .toCurrencyWithNetwork(tr.to_currency_with_network())

                .fromAmount(tr.from_amount())
                .expectedFromAmount(tr.expected_from_amount())
                .toAmount(tr.to_amount())
                .expectedToAmount(tr.expected_to_amount())
                .fromAmountInEur(tr.from_amount_in_eur())

                // --- Types de paiement ---
                .depositType(tr.deposit_type())
                .payoutType(tr.payout_type())
                .depositPaymentCategory(tr.deposit_payment_category())
                .payoutPaymentCategory(tr.payout_payment_category())

                // --- Extra ---
                .outputHash(tr.output_hash())
                .location(tr.location())
                .customerPayoutAddressChangeable(tr.customer_payout_address_changeable())

                // --- Payout info ---
                .payoutAddress(payoutAddress)
                .payoutExtraId(payoutExtraId)

                // --- Breakdown estimations ---
                // Ces valeurs ne viennent pas dans la réponse initiale (souvent dans TransactionDetailResponse)
                // → on les met à null par défaut
                .estimatedExchangeRate(null)
                .partnerFeeAmount(null)
                .partnerFeeCurrency(null)
                .partnerFeePercentage(null)
                .networkFeeAmount(null)
                .networkFeeCurrency(null)
                .serviceFeeAmount(null)
                .serviceFeeCurrency(null)

                // --- Dates ---
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())

                .build();
    }

    private Operation buildOperation(GuardarianTransaction tx, String designation) {
        // Convention wallet utilisateur :
        //  - ON_RAMP  (dépôt fiat → crypto sur le wallet)        => CREDIT
        //  - OFF_RAMP (retrait crypto → fiat hors du wallet)     => DEBIT
        // Cette inversion était la cause des "Retrait" affichés en vert sur
        // l'historique pro (cf. correctif côté front dans walletMapping.ts).
        return buildOperation(tx, designation, GUADARIAN_ONRAMP.equals(designation) ? "CREDIT"
                : GUADARIAN_OFFRAMP.equals(designation) ? "DEBIT" : "SWAPP");
    }

     private Operation buildOperation(GuardarianTransaction tx, String designation, String type) {
        Operation op = new Operation();
        op.setDesignation(designation);
        op.setOperationHash(String.valueOf(tx.getExternalTransactionId()));
        op.setType(type);
        op.setStatus(normalizeStatus(tx.getStatus()));
        op.setUsername(tx.getUsername());

        boolean isOffRamp = GUADARIAN_OFFRAMP.equals(designation);
        boolean isOnRamp = GUADARIAN_ONRAMP.equals(designation);

        if (isOffRamp) {
            // ══ OFF-RAMP : USDC → EUR (retrait) ══
            // L'utilisateur envoie des USDC et reçoit du fiat (EUR)
            op.setTransactionType("Retrait");
            op.setProvider("Guardarian");

            // Provider = ce qui est envoyé au provider (USDC)
            op.setProviderAmount(tx.getFromAmount());       // ex: 32.251 USDC
            op.setProviderDevise(tx.getFromCurrency());     // "USDC"

            // Devise locale = ce que l'utilisateur reçoit (EUR)
            op.setDevise(tx.getToCurrency());               // "EUR"

            // Montant affiché = le montant fiat que l'utilisateur reçoit
            // Priorité : toAmount (montant réel reçu) > fromAmountInEur > estimation
            Double fiatReceived = null;
            if (tx.getToAmount() != null && tx.getToAmount() > 0) {
                fiatReceived = tx.getToAmount();
            } else if (tx.getFromAmountInEur() != null && tx.getFromAmountInEur() > 0) {
                fiatReceived = tx.getFromAmountInEur();
            } else if (tx.getExpectedToAmount() != null && tx.getExpectedToAmount() > 0) {
                fiatReceived = tx.getExpectedToAmount();
            }

            op.setAmount(fiatReceived);
            op.setConvertedAmount(fiatReceived);

            // Original = montant fiat reçu (pour les détails)
            op.setOriginalAmount(fiatReceived);
            op.setOriginalDevise(tx.getToCurrency());       // "EUR"

        } else if (isOnRamp) {
            // ══ ON-RAMP : EUR → USDC (dépôt) ══
            // L'utilisateur envoie du fiat (EUR) et reçoit des USDC
            op.setTransactionType("Dépôt");
            op.setProvider("Guardarian");

            // Provider = ce qui est reçu du provider (USDC)
            op.setProviderAmount(tx.getToAmount());         // ex: 50 USDC
            op.setProviderDevise(tx.getToCurrency());       // "USDC"

            // Devise locale = ce que l'utilisateur a payé (EUR)
            op.setDevise(tx.getFromCurrency());             // "EUR"

            // Montant affiché = le montant fiat envoyé par l'utilisateur
            Double fiatSent = null;
            if (tx.getFromAmountInEur() != null && tx.getFromAmountInEur() > 0) {
                fiatSent = tx.getFromAmountInEur();
            } else if (tx.getFromAmount() != null && tx.getFromAmount() > 0) {
                fiatSent = tx.getFromAmount();
            } else if (tx.getExpectedFromAmount() != null && tx.getExpectedFromAmount() > 0) {
                fiatSent = tx.getExpectedFromAmount();
            }

            op.setAmount(fiatSent);
            op.setConvertedAmount(fiatSent);

            // Original = montant fiat envoyé (pour les détails)
            op.setOriginalAmount(fiatSent);
            op.setOriginalDevise(tx.getFromCurrency());     // "EUR"

        } else {
            // ══ SWAP ou autre ══
            op.setTransactionType("Swap");
            op.setProvider("Guardarian");
            op.setDevise(tx.getToCurrency());
            op.setAmount(tx.getToAmount());
            op.setConvertedAmount(tx.getFromAmountInEur());
            op.setProviderAmount(tx.getFromAmount());
            op.setProviderDevise(tx.getFromCurrency());
            op.setOriginalAmount(tx.getToAmount());
            op.setOriginalDevise(tx.getToCurrency());
        }

        // Dates
        if (op.getCreatedAt() == null) {
            op.setCreatedAt(tx.getCreatedAt() != null ? tx.getCreatedAt() : LocalDateTime.now());
        }
        if (op.getUpdatedAt() == null) {
            op.setUpdatedAt(tx.getUpdatedAt() != null ? tx.getUpdatedAt() : LocalDateTime.now());
        }

        return op;
    }
    
    /**
     * Normalise le statut Guardarian vers les statuts Operation standardisés
     * @param guardarianStatus Le statut retourné par Guardarian (ex: "new", "valide", "pending", etc.)
     * @return Le statut normalisé pour Operation (EN ATTENTE, VALIDEE, ANNULEE, REJETEE, NEW)
     */
    private String normalizeStatus(String guardarianStatus) {
        if (guardarianStatus == null) {
            return "NEW";
        }
        
        String statusLower = guardarianStatus.toLowerCase().trim();
        
        return switch (statusLower) {
            case "new", "pending", "waiting" -> "NEW";
            case "valide", "validated", "completed", "success", "successful" -> "VALIDEE";
            case "cancelled", "canceled", "cancelled_by_user" -> "ANNULEE";
            case "rejected", "failed", "error", "declined" -> "REJETEE";
            case "processing", "in_progress", "pending_payment" -> "EN ATTENTE";
            default -> {
                log.warn("Statut Guardarian non reconnu: {}, utilisation de 'NEW' par défaut", guardarianStatus);
                yield "NEW";
            }
        };
    }

    private String buildTransactionPayload(TransactionRequest request, Wallet wallet, boolean isOffRamp) throws JsonProcessingException {
        // offramp = retrait
        // onramp = deposit

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from_amount", request.from_amount());
        body.put("from_currency", request.from_currency());
        body.put("to_currency", request.to_currency());
        body.put("from_network", request.from_network());
        body.put("to_network", request.to_network());
        body.put("payout_extra_id", "");
        if (!isOffRamp) { // uniquement en depot Onramp
            body.put("payout_info", Map.of(
                "payout_address", wallet.getAddress(),
                "skip_choose_payout_address", false
            ));
        }

        if (!isOffRamp) {
            body.put("from_network", request.from_currency());
            body.put("to_network", MATIC);
        } else {
            if (request.from_network() != null) body.put("from_network", request.from_network());
            if (request.to_network() != null) body.put("to_network", request.to_network());
        }



        Map<String, Object> contactInfo = Map.of(
                "email", request.email(),
                "phone_number", request.username());
        body.put("customer", Map.of("contact_info", contactInfo));
        body.put("external_partner_link_id", request.username());

        return objectMapper.writeValueAsString(body);
    }

    private <T> T sendRequest(HttpRequest request, Class<T> clazz) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) return objectMapper.readValue(response.body(), clazz);
        throw new RuntimeException("Guardarian API error: " + response.body());
    }

    private HttpRequest.Builder createRequestBuilder(String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + endpoint))
                .header("x-api-key", apiKey)
                .header("Accept", "application/json");
    }

    private String resolveType(String fromCurrency, String toCurrency, Set<String> fiatCurrencies) {
        boolean fromIsFiat = fiatCurrencies.contains(fromCurrency.toUpperCase());
        boolean toIsFiat = fiatCurrencies.contains(toCurrency.toUpperCase());
        if (fromIsFiat && !toIsFiat) return GUADARIAN_ONRAMP;
        if (!fromIsFiat && toIsFiat) return GUADARIAN_OFFRAMP;
        if (!fromIsFiat && !toIsFiat) return GUADARIAN_SWAP;
        return "UNKNOWN";
    }

    private <T> void updateIfChanged(Supplier<T> getter, Consumer<T> setter, T newValue) {
        if (newValue != null && !Objects.equals(getter.get(), newValue)) setter.accept(newValue);
    }

    private void updateTransactionFields(GuardarianTransaction tx, TransactionDetailResponse r) {
        // --- Identifiants ---
        updateIfChanged(tx::getExternalTransactionId, tx::setExternalTransactionId,
                r.getId() != null ? Long.valueOf(r.getId()) : null);
        updateIfChanged(tx::getPartnerId, tx::setPartnerId,
                r.getPartnerId() != null ? Long.valueOf(r.getPartnerId()) : null);
        updateIfChanged(tx::getExternalPartnerLinkId, tx::setExternalPartnerLinkId, r.getExternalPartnerLinkId());
        
        // Mettre à jour le username depuis external_partner_link_id si disponible et si le username n'est pas déjà défini
        if (r.getExternalPartnerLinkId() != null && (tx.getUsername() == null || tx.getUsername().isBlank())) {
            tx.setUsername(r.getExternalPartnerLinkId());
        }

        // --- Client ---
        updateIfChanged(tx::getEmail, tx::setEmail, r.getEmail());

        // --- Statuts ---
        updateIfChanged(tx::getStatus, tx::setStatus, r.getStatus());
        updateIfChanged(tx::getStatusDetails, tx::setStatusDetails, r.getStatusDetails());

        // --- Monétaires & réseaux ---
        updateIfChanged(tx::getFromCurrency, tx::setFromCurrency, r.getFromCurrency());
        updateIfChanged(tx::getToCurrency, tx::setToCurrency, r.getToCurrency());
        updateIfChanged(tx::getFromNetwork, tx::setFromNetwork, r.getFromNetwork());
        updateIfChanged(tx::getToNetwork, tx::setToNetwork, r.getToNetwork());
        updateIfChanged(tx::getFromCurrencyWithNetwork, tx::setFromCurrencyWithNetwork, r.getFromCurrencyWithNetwork());
        updateIfChanged(tx::getToCurrencyWithNetwork, tx::setToCurrencyWithNetwork, r.getToCurrencyWithNetwork());

        updateIfChanged(tx::getFromAmount, tx::setFromAmount, r.getFromAmount());
        updateIfChanged(tx::getExpectedFromAmount, tx::setExpectedFromAmount, r.getExpectedFromAmount());
        updateIfChanged(tx::getToAmount, tx::setToAmount, r.getToAmount());
        updateIfChanged(tx::getExpectedToAmount, tx::setExpectedToAmount, r.getExpectedToAmount());
        updateIfChanged(tx::getFromAmountInEur, tx::setFromAmountInEur, r.getFromAmountInEur());

        // --- Paiements / Types ---
        updateIfChanged(tx::getDepositType, tx::setDepositType, r.getDepositType());
        updateIfChanged(tx::getPayoutType, tx::setPayoutType, r.getPayoutType());
        updateIfChanged(tx::getDepositPaymentCategory, tx::setDepositPaymentCategory, r.getDepositPaymentCategory());
        updateIfChanged(tx::getPayoutPaymentCategory, tx::setPayoutPaymentCategory, r.getPayoutPaymentCategory());

        // --- Extra ---
        updateIfChanged(tx::getOutputHash, tx::setOutputHash, r.getOutputHash());
        updateIfChanged(tx::getLocation, tx::setLocation, r.getLocation());

        // --- Payout (nested object) ---
        if (r.getPayout() != null) {
            updateIfChanged(tx::getPayoutAddress, tx::setPayoutAddress, r.getPayout().getAddress());
            updateIfChanged(tx::getPayoutExtraId, tx::setPayoutExtraId, r.getPayout().getExtraId());
        }

        // --- Estimate Breakdown ---
        if (r.getEstimateBreakdown() != null) {

            var eb = r.getEstimateBreakdown();

            // Exchange rate = toAmount / fromAmount if both exist
            if (eb.getFromAmount() != null && eb.getToAmount() != null && eb.getFromAmount() != 0) {
                double rate = eb.getToAmount() / eb.getFromAmount();
                updateIfChanged(tx::getEstimatedExchangeRate, tx::setEstimatedExchangeRate, rate);
            }

            // Partner Fee
            if (eb.getPartnerFee() != null) {
                updateIfChanged(tx::getPartnerFeeAmount, tx::setPartnerFeeAmount, eb.getPartnerFee().getAmount());
                updateIfChanged(tx::getPartnerFeeCurrency, tx::setPartnerFeeCurrency, eb.getPartnerFee().getCurrency());
                updateIfChanged(tx::getPartnerFeePercentage, tx::setPartnerFeePercentage, eb.getPartnerFee().getPercentage());
            }

            // Network Fee
            if (eb.getNetworkFee() != null) {
                updateIfChanged(tx::getNetworkFeeAmount, tx::setNetworkFeeAmount, eb.getNetworkFee().getAmount());
                updateIfChanged(tx::getNetworkFeeCurrency, tx::setNetworkFeeCurrency, eb.getNetworkFee().getCurrency());
            }

            // Service Fees → somme
            if (eb.getServiceFees() != null && !eb.getServiceFees().isEmpty()) {
                double totalServiceFee = eb.getServiceFees().stream()
                        .map(f -> f.getAmount() != null ? f.getAmount() : 0.0)
                        .reduce(0.0, Double::sum);

                updateIfChanged(tx::getServiceFeeAmount, tx::setServiceFeeAmount, totalServiceFee);

                // currency = premier service fee (au cas où)
                String currency = eb.getServiceFees().get(0).getCurrency();
                updateIfChanged(tx::getServiceFeeCurrency, tx::setServiceFeeCurrency, currency);
            }
        }

        // --- Dates ---
        if (r.getCreatedAt() != null && tx.getCreatedAt() == null) {
            tx.setCreatedAt(r.getCreatedAt().toLocalDateTime());
        }
        tx.setUpdatedAt(LocalDateTime.now());
    }

}
