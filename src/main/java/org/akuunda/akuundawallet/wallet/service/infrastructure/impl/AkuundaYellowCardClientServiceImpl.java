package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.transfert.impl.service.TransfertService;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.WebPaymentInitRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.service.WalletService;
import org.akuunda.akuundawallet.wallet.config.YellowCardChannelConfig;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaWalletClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.common.utils.YellowCardTextSanitizer;
import org.akuunda.akuundawallet.wallet.service.yellowcard.YellowCardWidgetQuoteAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AkuundaYellowCardClientServiceImpl implements AkuundaYellowCardClientService {

    private static final String CRYPTO_CURRENCY = "USDC";
    private static final String CUSTOMER_TYPE = "retail";
    private static final String CUSTOMER_TYPE_INSTITUTION = "institution";
    private static final String CRYPTO_NETWORK = "POLYGON";
    private static final String RAMP_TYPE_ON_RAMP = "deposit";
    private static final String RAMP_TYPE_OFF_RAMP = "withdraw";

    /**
     * Mapping statique : ancien networkId CI (envoyé par le frontend) → nom canonique du réseau.
     * Utilisé pour résoudre le nouveau networkId via l'API /networks?country=CI.
     */
    private static final Map<String, String> CI_OLD_NETWORK_ID_TO_NAME = Map.of(
            "a25d6c19-752e-4807-82ab-a60909c0c68e", "MTN",
            "ffb414e2-a7f9-49e4-aaf1-e26ea6d93465", "Orange",
            "8d18204e-b51f-4554-815d-71586d0dac13", "Wave",
            "e60702a6-76ab-4736-8b87-2f2fdad01eb8", "Moov"
    );

    private final TransfertService transfertService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final CurrencyFreaksClientService freaksClientService;
    private final WalletService walletService;
    private final AkunndaWalletClientService walletClientService;
    private final YellowCardChannelConfig channelConfig;
    private final TransactionNotificationHelper transactionNotificationHelper;
    private final YellowCardWidgetQuoteAggregator widgetQuoteAggregator;

    @Value("${yellowcard.api.base-url}")
    private String baseUrl;
    @Value("${yellowcard.api.key}")
    private String apiKey;
    @Value("${yellowcard.api.secret}")
    private String apiSecret;
    @Value("${akuunda.web-pay.base-url}")
    private String webPayBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------
    // Méthodes exposées
    // -----------------------

    @Override
    public ResponseEntity<TransactionStatusResponse> checkTransactionStatus(final String sequenceId, final String username) {

        log.info("get Transaction status with sequence id : {} for user: {}", sequenceId, username);
        log.debug("get Transaction status with sequence id : {} for user: {}", sequenceId, username);

        // Vérifier que le username est fourni
        if (username == null || username.isBlank()) {
            log.error("Username manquant pour la récupération de la transaction YellowCard sequenceId: {}", sequenceId);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // Vérifier que l'opération existe et appartient à l'utilisateur
        Operation operation = operationRepository.findByOperationHash(sequenceId);
        if (operation == null) {
            log.warn("Aucune opération trouvée pour sequenceId : {}", sequenceId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // Vérifier que l'opération appartient à l'utilisateur
        if (!operation.getUsername().equals(username)) {
            log.warn("Tentative d'accès non autorisée : transaction YellowCard {} demandée par {} mais appartient à {}", 
                    sequenceId, username, operation.getUsername());
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        // debit =  offramp
        // credit = onramp
        String url;
        String authorizationHeader;

        String timestamp = Instant.now().toString();

        if ("debit".equalsIgnoreCase(operation.getType())) {
            url = baseUrl + "/payments/sequence-id";
            authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                    "/business/payments/sequence-id/"  + sequenceId, timestamp);
        } else if ("credit".equalsIgnoreCase(operation.getType())) {
            url = baseUrl + "/collections/sequence-id";
            authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                    "/business/collections/sequence-id/"  + sequenceId, timestamp);
        } else {
            throw new IllegalArgumentException("Type d'opération inconnu : " + operation.getType());
        }

        if (sequenceId != null && !sequenceId.isEmpty()) {
            url += "/" + sequenceId;
        }

        HttpClient httpClient = createHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader)
                .header("X-YC-Timestamp", timestamp)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.debug("Transaction récupérée avec succès pour sequenceId : {}", sequenceId);
                final var responseBody = response.body();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(responseBody);

                // Extraction des champs spécifiques
                String status = rootNode.path("status").asText();
                String transactionId = rootNode.path("sequenceId").asText();
                
                // Vérifier que le phone dans la réponse YellowCard correspond à l'utilisateur
                // Pour OnRamp: phone est dans recipient.phone
                // Pour OffRamp: phone est dans sender.phone
                String phoneFromResponse = null;
                if (rootNode.has("sender") && rootNode.path("sender").has("phone")) {
                    phoneFromResponse = rootNode.path("sender").path("phone").asText();
                } else if (rootNode.has("recipient") && rootNode.path("recipient").has("phone")) {
                    phoneFromResponse = rootNode.path("recipient").path("phone").asText();
                }
                
                // Normaliser les numéros de téléphone pour la comparaison (enlever les + et espaces)
                String normalizedUsername = username.replaceAll("[+\\s]", "");
                String normalizedPhone = phoneFromResponse != null ? phoneFromResponse.replaceAll("[+\\s]", "") : null;
                
                // Vérifier que le phone correspond au username
                if (phoneFromResponse != null && !normalizedPhone.equals(normalizedUsername)) {
                    log.warn("Tentative d'accès non autorisée : transaction YellowCard {} demandée par {} mais phone dans réponse est {}", 
                            sequenceId, username, phoneFromResponse);
                    return new ResponseEntity<>(HttpStatus.FORBIDDEN);
                }

                TransactionStatusResponse transactionStatusResponse = new TransactionStatusResponse(
                        status,
                        username, // Utiliser le username fourni, pas celui extrait de la réponse
                        transactionId
                );

                if (!operation.getStatus().equals(status)) {
                    operation.setStatus(status);
                    operation.setUpdatedAt(LocalDateTime.now());
                    operationRepository.saveAndFlush(operation);
                    log.debug("Operation mise à jour avec succès pour sequenceId : {}", sequenceId);
                } else {
                    log.debug("Aucune mise à jour nécessaire pour l'opération avec sequenceId : {}", sequenceId);
                }

                return ResponseEntity.ok(transactionStatusResponse);
            } else {
                log.warn("Erreur lors de l'appel à l'API YellowCard avec sequenceId: {}: Code retour {} ",
                        sequenceId, response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Erreur interne lors de la récupération de la transaction avec erreur : {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<SimpleTransactionResponse> getSimpleTransactionStatus(final String sequenceId, final String username) {
        // Utiliser la méthode existante pour récupérer la transaction
        ResponseEntity<TransactionStatusResponse> response = checkTransactionStatus(sequenceId, username);
        
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            return ResponseEntity.status(response.getStatusCode()).build();
        }
        
        // Récupérer l'opération pour obtenir les détails
        Operation operation = operationRepository.findByOperationHash(sequenceId);
        if (operation == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        // Récupérer les détails depuis l'API YellowCard pour obtenir le montant et la devise
        try {
            String url;
            String authorizationHeader;
            String timestamp = Instant.now().toString();

            if ("debit".equalsIgnoreCase(operation.getType())) {
                url = baseUrl + "/payments/sequence-id/" + sequenceId;
                authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                        "/business/payments/sequence-id/" + sequenceId, timestamp);
            } else if ("credit".equalsIgnoreCase(operation.getType())) {
                url = baseUrl + "/collections/sequence-id/" + sequenceId;
                authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                        "/business/collections/sequence-id/" + sequenceId, timestamp);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            HttpClient httpClient = createHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authorizationHeader)
                    .header("X-YC-Timestamp", timestamp)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> apiResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (apiResponse.statusCode() == 200) {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(apiResponse.body());
                
                // Mapper vers SimpleTransactionResponse
                SimpleTransactionResponse simpleResponse = mapYellowCardToSimple(rootNode, sequenceId, operation);
                return ResponseEntity.ok(simpleResponse);
            } else {
                return ResponseEntity.status(apiResponse.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des détails YellowCard pour sequenceId: {}", sequenceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Mappe une transaction YellowCard vers le format simplifié
     */
    private SimpleTransactionResponse mapYellowCardToSimple(JsonNode rootNode, String sequenceId, Operation operation) {
        // Utiliser l'ID réel : sequenceId
        String id = sequenceId;
        
        // Mapper le statut
        String status = mapYellowCardStatus(rootNode.path("status").asText());
        
        // Extraire la date
        Instant date = operation.getCreatedAt() != null 
                ? operation.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                : (operation.getUpdatedAt() != null 
                        ? operation.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : Instant.now());
        
        // Extraire le montant et la devise depuis la réponse YellowCard
        // ⚠️ IMPORTANT : L'API YellowCard retourne les montants en centimes dans "convertedAmount"
        // (ex: "convertedAmount": 10000 = 100.00 XOF)
        // Pour OnRamp: amount dans recipient.amount ou convertedAmount, currency dans recipient.currency
        // Pour OffRamp: amount dans sender.amount ou convertedAmount, currency dans sender.currency
        Double amount = null;
        String currency = null;
        String type = null;
        
        // Priorité 1 : Utiliser convertedAmount si disponible (montant avec décimales, ex: 6557.49)
        if (rootNode.has("convertedAmount")) {
            JsonNode convertedAmountNode = rootNode.path("convertedAmount");
            if (!convertedAmountNode.isNull() && convertedAmountNode.isNumber()) {
                amount = convertedAmountNode.asDouble();
                log.debug("Utilisation de convertedAmount: {} pour transaction {}", amount, sequenceId);
            }
        }
        
        if (rootNode.has("recipient")) {
            JsonNode recipient = rootNode.path("recipient");
            // Si convertedAmount n'est pas disponible, utiliser recipient.amount
            if (amount == null && recipient.has("amount")) {
                amount = recipient.path("amount").asDouble();
            }
            if (recipient.has("currency")) {
                currency = recipient.path("currency").asText();
            }
            // Si recipient existe, c'est un ONRAMP (l'utilisateur reçoit)
            type = "ONRAMP";
        }
        
        if (amount == null && rootNode.has("sender")) {
            JsonNode sender = rootNode.path("sender");
            if (sender.has("amount")) {
                amount = sender.path("amount").asDouble();
            }
            if (currency == null && sender.has("currency")) {
                currency = sender.path("currency").asText();
            }
            // Si sender existe (et pas recipient), c'est un OFFRAMP (l'utilisateur envoie)
            if (type == null) {
                type = "OFFRAMP";
            }
        }
        
        // Fallback sur les champs de niveau racine
        if (amount == null && rootNode.has("amount")) {
            amount = rootNode.path("amount").asDouble();
        }
        if (currency == null && rootNode.has("currency")) {
            currency = rootNode.path("currency").asText();
        }
        
        // Fallback sur l'opération
        if (amount == null && operation.getAmount() != null) {
            amount = operation.getAmount();
        }
        if (currency == null && operation.getDevise() != null) {
            currency = operation.getDevise();
        }
        
        if (amount == null || currency == null) {
            log.warn("Montant ou devise manquant pour la transaction YellowCard: {}", sequenceId);
            return null;
        }
        
        // Déterminer le type depuis la designation si pas encore déterminé
        if (type == null && operation.getDesignation() != null) {
            String designation = operation.getDesignation().toUpperCase();
            if (designation.contains("ONRAMP") || designation.contains("ON RAMP")) {
                type = "ONRAMP";
            } else if (designation.contains("OFFRAMP") || designation.contains("OFF RAMP")) {
                type = "OFFRAMP";
            }
        }
        
        // Par défaut si on ne peut pas déterminer
        if (type == null) {
            type = "UNKNOWN";
        }
        
        // Filtrer : ne garder que les devises fiat (exclure USDC, USDT, BTC, ETH, etc.)
        if (isCryptoCurrency(currency)) {
            log.debug("Transaction exclue (crypto): {} - devise: {}", sequenceId, currency);
            return null;
        }
        
        // ⚠️ IMPORTANT : convertedAmount est en unités avec décimales (ex: 6557.49)
        // Utiliser directement la valeur avec les décimales (pas de multiplication par 100)
        // recipient.amount / sender.amount sont aussi en unités avec décimales, donc même traitement
        // Le champ amount dans SimpleTransactionResponse est maintenant Double pour garder les décimales
        
        return SimpleTransactionResponse.builder()
                .id(id)
                .status(status)
                .date(date)
                .amount(amount)
                .currency(currency)
                .operator("YELLOWCARD")
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

    @Override
    public ResponseEntity<List<SimpleTransactionResponse>> getSimpleTransactions(
            final String username,
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Integer skip,
            final Integer limit) {
        
        log.info("Récupération de l'historique YellowCard pour utilisateur: {} (from: {}, to: {}, skip: {}, limit: {})", 
                username, fromDate, toDate, skip, limit);
        
        // Vérifier que le username est fourni
        if (username == null || username.isBlank()) {
            log.error("Username manquant pour la récupération de l'historique YellowCard");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        
        try {
            // Récupérer les opérations YellowCard depuis la base de données
            // Filtrer par username et par designation (YellowCard OnRamp/OffRamp)
            List<Operation> allOperations = operationRepository.findByUsername(username, 
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
            
            // Filtrer uniquement les opérations YellowCard
            List<Operation> yellowCardOperations = allOperations.stream()
                    .filter(op -> op.getDesignation() != null && 
                            (op.getDesignation().contains("Yellow Card") || 
                             op.getDesignation().contains("YellowCard")))
                    .filter(op -> {
                        // Filtrer par dates si fournies
                        if (fromDate != null && op.getCreatedAt() != null && op.getCreatedAt().isBefore(fromDate)) {
                            return false;
                        }
                        if (toDate != null && op.getCreatedAt() != null && op.getCreatedAt().isAfter(toDate)) {
                            return false;
                        }
                        return true;
                    })
                    .sorted((a, b) -> {
                        // Trier par date décroissante (plus récentes en premier)
                        LocalDateTime dateA = a.getCreatedAt() != null ? a.getCreatedAt() : a.getUpdatedAt();
                        LocalDateTime dateB = b.getCreatedAt() != null ? b.getCreatedAt() : b.getUpdatedAt();
                        if (dateA == null && dateB == null) return 0;
                        if (dateA == null) return 1;
                        if (dateB == null) return -1;
                        return dateB.compareTo(dateA);
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            log.info("Trouvé {} opérations YellowCard pour l'utilisateur {}", yellowCardOperations.size(), username);
            
            // Appliquer la pagination
            int skipValue = skip != null && skip >= 0 ? skip : 0;
            int limitValue = limit != null && limit > 0 ? limit : 100;
            
            List<Operation> paginatedOperations = yellowCardOperations.stream()
                    .skip(skipValue)
                    .limit(limitValue)
                    .collect(java.util.stream.Collectors.toList());
            
            // Pour chaque opération, récupérer les détails depuis l'API YellowCard
            List<SimpleTransactionResponse> transactions = new ArrayList<>();
            
            for (Operation operation : paginatedOperations) {
                try {
                    String sequenceId = operation.getOperationHash();
                    if (sequenceId == null || sequenceId.isBlank()) {
                        log.warn("SequenceId manquant pour l'opération {}", operation.getId());
                        continue;
                    }
                    
                    // Récupérer les détails depuis l'API YellowCard
                    ResponseEntity<SimpleTransactionResponse> transactionResponse = 
                            getSimpleTransactionStatus(sequenceId, username);
                    
                    if (transactionResponse.getStatusCode() == HttpStatus.OK && 
                        transactionResponse.getBody() != null) {
                        transactions.add(transactionResponse.getBody());
                    } else {
                        log.warn("Impossible de récupérer les détails pour la transaction {} (status: {})", 
                                sequenceId, transactionResponse.getStatusCode());
                        // Créer une réponse simplifiée depuis l'opération en BD si l'API échoue
                        SimpleTransactionResponse fallback = createSimpleTransactionFromOperation(operation, sequenceId);
                        if (fallback != null) {
                            transactions.add(fallback);
                        }
                    }
                } catch (Exception e) {
                    log.error("Erreur lors de la récupération des détails pour l'opération {}: {}", 
                            operation.getId(), e.getMessage(), e);
                    // Continuer avec la transaction suivante
                }
            }
            
            log.info("Retour de {} transactions YellowCard pour l'utilisateur {}", transactions.size(), username);
            return ResponseEntity.ok(transactions);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération de l'historique YellowCard pour utilisateur {}: {}", 
                    username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Crée une SimpleTransactionResponse depuis une opération en base de données
     * (fallback si l'API YellowCard n'est pas disponible)
     */
    private SimpleTransactionResponse createSimpleTransactionFromOperation(Operation operation, String sequenceId) {
        try {
            // Utiliser l'ID réel : sequenceId
            String id = sequenceId;
            String status = mapYellowCardStatus(operation.getStatus());
            
            Instant date = operation.getCreatedAt() != null 
                    ? operation.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                    : (operation.getUpdatedAt() != null 
                            ? operation.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                            : Instant.now());
            
            Double amount = operation.getAmount();
            String currency = operation.getDevise();
            
            if (amount == null || currency == null) {
                log.warn("Montant ou devise manquant pour l'opération {}", operation.getId());
                return null;
            }
            
            // Déterminer le type depuis la designation
            String type = "UNKNOWN";
            if (operation.getDesignation() != null) {
                String designation = operation.getDesignation().toUpperCase();
                if (designation.contains("ONRAMP") || designation.contains("ON RAMP")) {
                    type = "ONRAMP";
                } else if (designation.contains("OFFRAMP") || designation.contains("OFF RAMP")) {
                    type = "OFFRAMP";
                }
            }
            
            // Filtrer : ne garder que les devises fiat (exclure USDC, USDT, BTC, ETH, etc.)
            if (isCryptoCurrency(currency)) {
                log.debug("Transaction exclue (crypto): {} - devise: {}", sequenceId, currency);
                return null;
            }
            
            // ⚠️ IMPORTANT : Les montants en base de données sont en unités avec décimales
            // Utiliser directement la valeur avec les décimales (pas de multiplication par 100)
            // Le champ amount dans SimpleTransactionResponse est maintenant Double pour garder les décimales
            
            return SimpleTransactionResponse.builder()
                    .id(id)
                    .status(status)
                    .date(date)
                    .amount(amount)
                    .currency(currency)
                    .operator("YELLOWCARD")
                    .type(type)
                    .build();
        } catch (Exception e) {
            log.error("Erreur lors de la création de SimpleTransactionResponse depuis l'opération {}: {}", 
                    operation.getId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Génère un ID simplifié au format AKU-YYYYMMDD-XXXX
     */
    private String generateSimpleTransactionId(Operation operation, String sequenceId) {
        LocalDateTime date = operation.getCreatedAt() != null ? operation.getCreatedAt() : operation.getUpdatedAt();
        if (date == null) {
            date = LocalDateTime.now();
        }
        
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // Utiliser les 4 derniers caractères du sequenceId
        String uniqueId = sequenceId.length() >= 4 
                ? sequenceId.substring(sequenceId.length() - 4).toUpperCase()
                : String.format("%04d", Math.abs(sequenceId.hashCode() % 10000));
        
        return String.format("AKU-%s-%s", dateStr, uniqueId);
    }

    /**
     * Mappe le statut YellowCard vers le format simplifié
     */
    private String mapYellowCardStatus(String yellowCardStatus) {
        if (yellowCardStatus == null) {
            return "pending";
        }
        return switch (yellowCardStatus.toLowerCase()) {
            case "complete", "completed" -> "completed";
            case "pending", "processing" -> "pending";
            case "failed", "cancelled" -> "failed";
            default -> yellowCardStatus.toLowerCase();
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
    public ResponseEntity<Object> createCollection(OnRampRequest request) {
        if (request == null) {
            return new ResponseEntity<>("OnRampRequest ne peut pas être null", HttpStatus.BAD_REQUEST);
        }
        log.info("Creating OnRamp collection for user: {}", request.getRecipient() == null
                ? "unknown" : safeValue(request.getRecipient().getPhone()));

        String sequenceId = generateSequenceId();
        String url = baseUrl + "/collections";
        String timestamp = Instant.now().toString();

        // ⚠️ IMPORTANT : Utiliser le taux YellowCard buy directement (pas Currency Freaks)
        // Cela garantit la cohérence avec le calcul des frais qui utilise aussi le taux YellowCard
        // Conversion : USD = XOF / Taux_YellowCard_buy
        BigDecimal convertedAmountUSD = getConvertedAmountUsingYellowCardBuyRate(
                request.getCurrency(), request.getAmount(), request.getCountry());

        if (convertedAmountUSD.compareTo(BigDecimal.ONE) < 0) {
            return new ResponseEntity<>("Le montant a transferer en USD doit etre superieur ou egal a 1 alors que nous avons : " +
                     convertedAmountUSD.toString(), HttpStatus.BAD_REQUEST);
        }

        // 1️⃣ Vérification utilisateur et wallet
        String recipientPhone = request.getRecipient() != null ? request.getRecipient().getPhone() : null;
        if ((recipientPhone == null || recipientPhone.isBlank()) && request.getSource() != null) {
            recipientPhone = request.getSource().getPhoneNumber();
            if (recipientPhone == null || recipientPhone.isBlank()) {
                recipientPhone = request.getSource().getAccountNumber();
            }
        }
        Users user = userRepository.getUsersByUsername(recipientPhone);
        if (user == null) {
            return new ResponseEntity<>("Utilisateur non trouvé pour le username donné", HttpStatus.NOT_FOUND);
        }

        // Vérifier que le userId (customerUID) existe
        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            log.error("❌ Impossible de récupérer le customerUID: customerUID introuvable pour l'utilisateur {}", 
                    recipientPhone);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", "Impossible de récupérer le customerUID: customerUID introuvable"
                    ));
        }

        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet == null) {
            return new ResponseEntity<>("Portefeuille non trouvé pour l'utilisateur donné", HttpStatus.NOT_FOUND);
        }

        // 🔄 Résolution automatique du channelId basée sur le networkId
        if (request.getSource() != null && request.getSource().getNetworkId() != null) {
            String originalChannelId = request.getChannelId();
            String networkId = request.getSource().getNetworkId();
            String resolvedChannelId = resolveChannelIdForNetwork(
                    originalChannelId, 
                    networkId, 
                    request.getCountry(), 
                    RAMP_TYPE_ON_RAMP
            );
            
            if (!resolvedChannelId.equals(originalChannelId)) {
                log.info("🔄 Remplacement du channelId pour OnRamp: {} → {} (networkId: {}, country: {})", 
                        originalChannelId, resolvedChannelId, networkId, request.getCountry());
                request.setChannelId(resolvedChannelId);
            }
        }

        sanitizeOnRampTextFields(request);

        // 2️⃣ Construction du body JSON proprement
        // IMPORTANT: Envoyer la valeur brute en USD à YellowCard (ex: 3.532807416069328)
        String requestBody = buildRequestBody(request, sequenceId, wallet.getAddress(),
                user.getUserId(), convertedAmountUSD);

        String authorizationHeader = generateAuthorizationHeaderWithBody(
                apiKey, apiSecret, "/business/collections", "POST", timestamp, requestBody
        );

        // 3️⃣ Appel API externe
        var response = getObjectResponseEntity(url, requestBody, authorizationHeader, timestamp);
        if (!response.getStatusCode().is2xxSuccessful()) {
            String message = "Erreur lors de l'appel à l'API YellowCard: " + response.getBody();
            log.error(message);
            log.error("📤 Request body envoyé à YellowCard (OnRamp): {}", requestBody);
            return new ResponseEntity<>(message, response.getStatusCode());
        }

        // ⚠️ CRITIQUE : Logger la réponse complète de YellowCard pour diagnostic
        log.info("📥 Réponse YellowCard OnRamp (Status: {}): {}", response.getStatusCode(), response.getBody());
        
        // ⚠️ ATTENTION : YellowCard peut retourner 200/201 juste pour confirmer que la requête est acceptée,
        // mais le wallet blockchain n'est crédité que lorsque YellowCard a réellement traité le paiement (asynchrone).
        // Il faut vérifier le statut de la transaction dans la réponse avant de mettre à jour le wallet.
        // 
        // PROBLÈME POTENTIEL : Si on met à jour le wallet local maintenant, mais que YellowCard n'a pas encore
        // crédité le wallet blockchain, il y aura une incohérence entre le wallet local et le wallet blockchain.
        //
        // SOLUTION RECOMMANDÉE :
        // 1. Vérifier le statut dans la réponse YellowCard (si disponible)
        // 2. Ne mettre à jour le wallet que si le statut indique que la transaction est complétée/success
        // 3. Sinon, attendre un webhook de YellowCard ou vérifier le statut plus tard
        
        // Pour l'instant, on met à jour le wallet mais avec un avertissement dans les logs
        log.warn("⚠️ Mise à jour du wallet local effectuée, mais vérifiez que YellowCard a bien crédité le wallet blockchain. " +
                "Si le wallet blockchain n'a pas été crédité, il y aura une incohérence.");

        // Extraire bankInfo si présent dans la réponse YellowCard (paiement par virement bancaire)
        Map<String, Object> bankInfo = extractBankInfoFromResponse(response.getBody());
        if (bankInfo != null) {
            log.info("📋 Réponse YellowCard avec bankInfo (virement bancaire) pour user: {}", recipientPhone);
            Map<String, Object> responseMap = new LinkedHashMap<>();
            responseMap.put("success", true);
            responseMap.put("transactionId", sequenceId);
            responseMap.put("status", bankInfo.get("status") != null ? bankInfo.get("status") : "pending");
            responseMap.put("bankInfo", bankInfo.get("bankInfo"));
            responseMap.put("reference", bankInfo.get("reference"));
            responseMap.put("expiresAt", bankInfo.get("expiresAt"));
            responseMap.put("amount", request.getAmount());
            responseMap.put("currency", request.getCurrency());
            responseMap.put("yellowCardResponse", response.getBody());

            // 4️⃣ Mise à jour du wallet et création de l'opération
            createOnRampOperationPending(wallet, request, sequenceId, convertedAmountUSD);

            return ResponseEntity.status(response.getStatusCode()).body(responseMap);
        }

        // 4️⃣ Mise à jour du wallet et création de l'opération
        // ⚠️ IMPORTANT : Passer le montant en USDC (convertedAmountUSD) pour mettre à jour le wallet
        // Le wallet blockchain reçoit des USDC, donc le wallet local doit aussi être mis à jour en USDC
        createOnRampOperationPending(wallet, request, sequenceId, convertedAmountUSD);

        // 5️⃣ Retour
        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
    }

    @Override
    public ResponseEntity<Object> createCollectionForPermanentLink(OnRampRequest request,
                                                                   String create2WalletAddress,
                                                                   String creatorUserId) {
        if (request == null) {
            return new ResponseEntity<>("OnRampRequest ne peut pas être null", HttpStatus.BAD_REQUEST);
        }
        if (create2WalletAddress == null || create2WalletAddress.isBlank()) {
            return new ResponseEntity<>("L'adresse CREATE2 est obligatoire", HttpStatus.BAD_REQUEST);
        }
        if (creatorUserId == null || creatorUserId.isBlank()) {
            return new ResponseEntity<>("L'identifiant du créateur est obligatoire", HttpStatus.BAD_REQUEST);
        }

        log.info("Creating permanent link OnRamp collection, create2Wallet: {}", create2WalletAddress);

        String sequenceId = generateSequenceId();
        String url = baseUrl + "/collections";
        String timestamp = Instant.now().toString();

        BigDecimal convertedAmountUSD = getConvertedAmountUsingYellowCardBuyRate(
                request.getCurrency(), request.getAmount(), request.getCountry());

        if (convertedAmountUSD.compareTo(BigDecimal.ONE) < 0) {
            return new ResponseEntity<>("Le montant à transférer en USD doit être supérieur ou égal à 1. Montant converti: " +
                    convertedAmountUSD, HttpStatus.BAD_REQUEST);
        }

        if (request.getSource() != null && request.getSource().getNetworkId() != null) {
            String originalChannelId = request.getChannelId();
            String networkId = request.getSource().getNetworkId();
            String resolvedChannelId = resolveChannelIdForNetwork(
                    originalChannelId,
                    networkId,
                    request.getCountry(),
                    RAMP_TYPE_ON_RAMP
            );
            if (!resolvedChannelId.equals(originalChannelId)) {
                log.info("Remplacement du channelId pour PermanentLink OnRamp: {} -> {} (networkId: {}, country: {})",
                        originalChannelId, resolvedChannelId, networkId, request.getCountry());
                request.setChannelId(resolvedChannelId);
            }
        }

        sanitizeOnRampTextFields(request);

        String requestBody = buildRequestBody(request, sequenceId, create2WalletAddress, creatorUserId, convertedAmountUSD);

        String authorizationHeader = generateAuthorizationHeaderWithBody(
                apiKey, apiSecret, "/business/collections", "POST", timestamp, requestBody
        );

        var response = getObjectResponseEntity(url, requestBody, authorizationHeader, timestamp);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("❌ YellowCard a refusé la collection PermanentLink (status={}, sequenceId={}): {}",
                    response.getStatusCode(), sequenceId, response.getBody());
            log.error("📤 Request body envoyé à YellowCard (PermanentLink): {}", requestBody);

            // YellowCard renvoie typiquement du JSON ; on tente de le parser pour le propager
            // tel quel dans `details`. Si ça échoue (ex. timeout, body texte brut), on garde
            // la chaîne brute. Cela évite au frontend de tomber sur du texte non-JSON et
            // donc d'afficher un générique "Une erreur est survenue".
            Object yellowCardDetails = response.getBody();
            if (yellowCardDetails instanceof String s) {
                try {
                    yellowCardDetails = objectMapper.readTree(s);
                } catch (Exception ignored) {
                    // body non-JSON — on laisse la chaîne telle quelle
                }
            }

            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("code", "YELLOWCARD_ERROR");
            errorBody.put("message", "YellowCard a refusé la requête de collection.");
            errorBody.put("status", response.getStatusCode().value());
            errorBody.put("sequenceId", sequenceId);
            errorBody.put("details", yellowCardDetails);
            return new ResponseEntity<>(errorBody, response.getStatusCode());
        }

        log.info("Permanent link OnRamp collection créée. sequenceId: {}", sequenceId);

        try {
            Optional<Users> creatorOpt = userRepository.findById(creatorUserId);
            if (creatorOpt.isEmpty()) {
                log.warn("Permanent link OnRamp: créateur introuvable userId={}, sequenceId={}", creatorUserId, sequenceId);
            } else {
                Wallet wallet = walletRepository.findByUsers(creatorOpt.get());
                if (wallet == null) {
                    log.warn("Permanent link OnRamp: wallet introuvable pour userId={}, sequenceId={}", creatorUserId, sequenceId);
                } else {
                    createOnRampOperationPending(wallet, request, sequenceId, convertedAmountUSD);
                }
            }
        } catch (Exception ex) {
            log.error("Permanent link OnRamp: échec création opération PENDING pour sequenceId={}: {}", sequenceId, ex.getMessage(), ex);
        }

        Map<String, Object> bankInfo = extractBankInfoFromResponse(response.getBody());

        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("success", true);
        responseMap.put("transactionId", sequenceId);
        responseMap.put("yellowCardResponse", response.getBody());

        if (bankInfo != null) {
            responseMap.put("bankInfo", bankInfo.get("bankInfo"));
            responseMap.put("reference", bankInfo.get("reference"));
            responseMap.put("expiresAt", bankInfo.get("expiresAt"));
            responseMap.put("status", bankInfo.get("status") != null ? bankInfo.get("status") : "pending");
            log.info("📋 Permanent link avec bankInfo pour sequenceId: {}", sequenceId);
        }

        return ResponseEntity.status(response.getStatusCode()).body(responseMap);
    }

    @Override
    public ResponseEntity<Object> createOffRampPaiements(OffRampRequest request,
                                                         String headerPin, String username) {

        log.info("Creating OffRamp payment for user: {} and body : {} ", username, request);
        log.debug("Creating OffRamp payment for user: {} and body : {} ", username, request);

        String url = baseUrl + "/payments";
        String sequenceId = generateSequenceId();
        String timestamp = Instant.now().toString();

        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            return new ResponseEntity<>("utilisateur non trouvé avec username " + username, HttpStatus.NOT_FOUND);
        }
        
        // Vérifier que le userId (customerUID) existe
        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            log.error("❌ Impossible de récupérer le customerUID: customerUID introuvable pour l'utilisateur {}", username);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "error", "Impossible de récupérer le customerUID: customerUID introuvable"
                    ));
        }
        
        String customerID = user.getUserId();

        // Vérifier settlementInfo sécurité
        if (request == null || request.getSettlementInfo() == null) {
            return new ResponseEntity<>("Requête OffRamp invalide: settlementInfo manquant", HttpStatus.BAD_REQUEST);
        }

        // ⚠️ CRITIQUE : Comme dans OnRamp, s'assurer que YellowCard reçoit exactement le montant saisi par l'utilisateur
        // Si l'utilisateur saisit 2500 NGN, YellowCard doit recevoir l'équivalent exact de 2500 NGN en USD
        // Après conversion USD → NGN de leur côté, YellowCard doit retrouver exactement 2500 NGN
        // Les frais que YellowCard enlève sont leur problème, mais nous on doit s'assurer que les 2500 NGN arrivent chez eux
        // ⚠️ IMPORTANT : Utiliser cryptoAmount depuis settlementInfo (pas request.getAmount() qui peut être 0)
        double requestedAmount = request.getSettlementInfo().getCryptoAmount();
        
        // Vérification que cryptoAmount est valide (non nul et > 0)
        if (requestedAmount <= 0) {
            log.error("❌ cryptoAmount invalide ou manquant dans settlementInfo: {}", requestedAmount);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Impossible de récupérer le customerUID: customerUID introuvable",
                            "transactionId", ""
                    ));
        }
        
        log.info("🎯 OffRamp: Montant saisi par l'utilisateur (cryptoAmount) = {} {} (YellowCard doit recevoir exactement ce montant)", 
                requestedAmount, request.getCurrency());
        
        // Conversion : USD = Devise locale / Taux_YellowCard_sell
        // ⚠️ CRITIQUE : Convertir directement le montant demandé (comme dans OnRamp où on convertit le montant initial)
        // YellowCard recevra donc l'équivalent exact du montant demandé
        // Quand YellowCard reconvertira USD → NGN avec le même taux sell, ils retrouveront exactement 2500 NGN
        // ⚠️ IMPORTANT : Utiliser requestedAmount (qui vient de cryptoAmount), PAS request.getAmount()
        BigDecimal convertedAmountUSD = getConvertedAmountUsingYellowCardSellRate(
                request.getCurrency(), requestedAmount, request.getCountry());
        
        log.info("✅ Conversion: {} {} / Taux_sell = {} USD (valeur brute, toutes décimales préservées)", 
                requestedAmount, request.getCurrency(), convertedAmountUSD);
        log.info("✅ Vérification: {} USD × Taux_sell = {} {} (YellowCard doit retrouver exactement ce montant)", 
                convertedAmountUSD, requestedAmount, request.getCurrency());
        log.info("📤 Ce montant USD sera envoyé à YellowCard dans settlementInfo.cryptoAmount");

        if (convertedAmountUSD.compareTo(BigDecimal.ONE) < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Le montant a transferer en USD doit etre superieur ou egal a 1 alors que nous avons : " + convertedAmountUSD,
                            "transactionId", ""
                    ));
        }

        // ✅ Vérification du solde USDC
        // ⚠️ IMPORTANT : Vérifier le solde en USDC avec le montant converti en USD
        // Le wallet stocke le solde en USDC, donc on doit comparer directement en USDC
        var balanceCheck = verifyUserBalance(username, request, convertedAmountUSD);
        if (!balanceCheck.getStatusCode().is2xxSuccessful()) {
            return balanceCheck;
        }
        
        // ✅ Vérification du solde de gas (MATIC)
        // ⚠️ CRITIQUE : Vérifier que l'utilisateur a assez de MATIC pour payer les frais de transaction
        // Sans gas, la transaction échouera avec une erreur 403 Forbidden
        var gasBalanceCheck = verifyGasBalance(username);
        if (!gasBalanceCheck.getStatusCode().is2xxSuccessful()) {
            return gasBalanceCheck;
        }

        // 🔍 Résolution dynamique du channelId pour le corridor CI (OffRamp uniquement)
        if ("CI".equalsIgnoreCase(request.getCountry())) {
            try {
                log.info("🔍 Résolution dynamique du channelId withdraw pour CI");
                ResponseEntity<String> channelsResponse = getChannels("CI");

                if (channelsResponse.getStatusCode().is2xxSuccessful() && channelsResponse.getBody() != null) {
                    JsonNode rootNode = objectMapper.readTree(channelsResponse.getBody());
                    JsonNode channelsArray = rootNode.has("channels") ? rootNode.get("channels") : rootNode;

                    if (channelsArray.isArray()) {
                        String resolvedChannelId = null;
                        long resolvedApiMax = 0;

                        for (JsonNode channel : channelsArray) {
                            String rampType = channel.has("rampType") ? channel.get("rampType").asText() : "";
                            String status = channel.has("status") ? channel.get("status").asText() : "";
                            String apiStatus = channel.has("apiStatus") ? channel.get("apiStatus").asText() : "";
                            long apiMax = channel.has("apiMax") ? channel.get("apiMax").asLong(0) : 0;
                            long apiMin = channel.has("apiMin") ? channel.get("apiMin").asLong(0) : 0;

                            if ("withdraw".equalsIgnoreCase(rampType)
                                    && "active".equalsIgnoreCase(status)
                                    && "active".equalsIgnoreCase(apiStatus)
                                    && apiMax > 0
                                    && apiMin > 0
                                    && apiMax > resolvedApiMax) {
                                resolvedChannelId = channel.has("id") ? channel.get("id").asText() : null;
                                resolvedApiMax = apiMax;
                            }
                        }

                        if (resolvedChannelId != null) {
                            String originalChannelId = request.getChannelId();
                            if (!resolvedChannelId.equals(originalChannelId)) {
                                log.info("🔄 Remplacement channelId pour OffRamp CI: {} → {}", originalChannelId, resolvedChannelId);
                                request.setChannelId(resolvedChannelId);
                            } else {
                                log.info("✅ ChannelId déjà correct pour OffRamp CI: {}", originalChannelId);
                            }
                        } else {
                            log.warn("⚠️ Aucun channel withdraw actif trouvé pour CI, conservation du channelId original: {}", request.getChannelId());
                        }
                    }
                } else {
                    log.warn("⚠️ Impossible de récupérer les channels pour CI, conservation du channelId original");
                }
            } catch (Exception e) {
                log.error("❌ Erreur lors de la résolution du channelId pour CI: {}", e.getMessage(), e);
            }

            // 🔄 Résolution du networkId CI : mapper l'ancien networkId vers le nouveau via /networks?country=CI
            if (request.getDestination() != null) {
                String oldNetworkId = request.getDestination().getNetworkId();
                String networkName = CI_OLD_NETWORK_ID_TO_NAME.get(oldNetworkId);
                if (networkName != null) {
                    log.info("🔍 Ancien networkId CI détecté: {} (réseau: {}), résolution du nouveau networkId via l'API", oldNetworkId, networkName);
                    try {
                        ResponseEntity<String> networksResponse = getNetworks("CI");
                        if (networksResponse.getStatusCode().is2xxSuccessful() && networksResponse.getBody() != null) {
                            JsonNode networksRoot = objectMapper.readTree(networksResponse.getBody());
                            JsonNode networksArray = networksRoot.has("networks") ? networksRoot.get("networks") : networksRoot;
                            if (networksArray.isArray()) {
                                for (JsonNode network : networksArray) {
                                    String apiName = network.has("name") ? network.get("name").asText() : "";
                                    if (networkName.equalsIgnoreCase(apiName)) {
                                        String newNetworkId = network.has("id") ? network.get("id").asText() : null;
                                        if (newNetworkId != null && !newNetworkId.equals(oldNetworkId)) {
                                            log.info("🔄 Remplacement networkId pour OffRamp CI: {} → {} (réseau: {})", oldNetworkId, newNetworkId, networkName);
                                            request.getDestination().setNetworkId(newNetworkId);
                                        } else {
                                            log.info("✅ NetworkId déjà correct pour OffRamp CI: {}", oldNetworkId);
                                        }
                                        break;
                                    }
                                }
                            }
                        } else {
                            log.warn("⚠️ Impossible de récupérer les networks pour CI, conservation du networkId original: {}", oldNetworkId);
                        }
                    } catch (Exception e) {
                        log.error("❌ Erreur lors de la résolution du networkId pour CI: {}", e.getMessage(), e);
                    }
                }
            }
        }

        sanitizeOffRampTextFields(request);

        // 1️⃣ Construction du corps JSON avec safeValue
        // IMPORTANT: Envoyer la valeur brute en USD à YellowCard (ex: 3.532807416069328)
        String requestBody = buildOffRampRequestBody(request, sequenceId, customerID, convertedAmountUSD);

        // 2️⃣ Génération du header d’autorisation
        String authorizationHeader = generateAuthorizationHeaderWithBody(
                apiKey, apiSecret, "/business/payments", "POST", timestamp, requestBody
        );

        // 3️⃣ Appel API externe
        var response = getObjectResponseEntity(url, requestBody, authorizationHeader, timestamp);
        if (!response.getStatusCode().is2xxSuccessful()) {
            String errorDetails = response.getBody() != null ? response.getBody().toString() : "Pas de détails disponibles";
            log.error("❌ Erreur API YellowCard OffRamp (Status: {}): {}", response.getStatusCode(), errorDetails);
            log.error("📤 Body JSON envoyé à YellowCard: {}", requestBody);
            // Retourner l'erreur détaillée de YellowCard pour faciliter le débogage
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Échec de la création du paiement OffRamp \n " + errorDetails,
                            "transactionId", ""
                    ));
        }

        // 4️⃣ Traitement de la réponse JSON
        return handleOffRampResponse(response, username, headerPin, sequenceId, request, convertedAmountUSD);
    }

    @Override
    public ResponseEntity<String> getChannels(final String countryCode) {
        String url = baseUrl + "/channels";
        if (countryCode != null) {
            url += "?country=" + countryCode;
        }

        String timestamp = Instant.now().toString();

        String authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                "/business/channels", timestamp);

        HttpClient httpClient = createHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader)
                .header("X-YC-Timestamp", timestamp)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return ResponseEntity.ok(response.body());
            } else {
                log.error("Erreur lors de l'appel à l'API YellowCard: Code retour {}", response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Erreur interne: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<String> getRates(String currencyCode) {
        String url = baseUrl + "/rates";
        if (currencyCode != null) {
            url += "?currency=" + currencyCode;
        }

        String timestamp = Instant.now().toString();

        String authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                "/business/rates", timestamp);

        HttpClient httpClient = createHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader)
                .header("X-YC-Timestamp", timestamp)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return ResponseEntity.ok(response.body());
            } else {
                log.error("Erreur lors de l'appel à l'API YellowCard: Code retour {}", response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Erreur interne: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<String> getWidgetQuote(YellowCardWidgetQuoteRequest request) {
        if (request != null && request.getLocalAmount() != null && request.getLocalAmount() > 0) {
            return widgetQuoteAggregator.aggregate(
                    request,
                    this::invokeSingleWidgetQuote,
                    this::fetchRateForLocalFallback);
        }
        return invokeSingleWidgetQuote(request);
    }

    ResponseEntity<String> invokeSingleWidgetQuote(YellowCardWidgetQuoteRequest request) {
        if (request == null || request.getCurrency() == null || request.getCurrency().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"currency requis\"}");
        }
        if (request.getChannelId() == null || request.getChannelId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"error\":\"channelId requis\"}");
        }

        String coin = request.getCoin() != null && !request.getCoin().isBlank() ? request.getCoin() : CRYPTO_CURRENCY;
        String network = request.getNetwork() != null && !request.getNetwork().isBlank()
                ? request.getNetwork() : CRYPTO_NETWORK;
        String transactionType = request.getTransactionType() != null && !request.getTransactionType().isBlank()
                ? request.getTransactionType() : "Sell";
        boolean isBuy = "Buy".equalsIgnoreCase(transactionType.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currency", request.getCurrency().trim().toUpperCase());
        body.put("coin", coin);
        body.put("network", network);
        body.put("channelId", request.getChannelId());
        body.put("transactionType", transactionType);

        if (isBuy) {
            if (request.getLocalAmount() == null || request.getLocalAmount() <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"localAmount requis pour transactionType Buy\"}");
            }
            body.put("localAmount", request.getLocalAmount());
        } else if (request.getCryptoAmount() != null && request.getCryptoAmount() > 0) {
            body.put("cryptoAmount", request.getCryptoAmount());
        } else if (request.getLocalAmount() != null && request.getLocalAmount() > 0) {
            BigDecimal usd = getConvertedAmountUsingYellowCardSellRate(
                    request.getCurrency().trim().toUpperCase(),
                    request.getLocalAmount(),
                    request.getCountry());
            if (usd.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"Impossible de convertir le montant local en USDC\"}");
            }
            body.put("cryptoAmount", usd.doubleValue());
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"localAmount ou cryptoAmount requis\"}");
        }

        String url = baseUrl + "/widget/quote";
        String timestamp = Instant.now().toString();
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.error("Erreur sérialisation quote widget: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        String authorizationHeader = generateAuthorizationHeaderWithBody(
                apiKey, apiSecret, "/business/widget/quote", "POST", timestamp, requestBody);

        HttpClient httpClient = createHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader)
                .header("X-YC-Timestamp", timestamp)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Quote widget YellowCard OK type={} {} {} channelId={}",
                        transactionType, request.getLocalAmount(), request.getCurrency(), request.getChannelId());
                return ResponseEntity.ok(response.body());
            }
            log.warn("Quote widget YellowCard échec {}: {}", response.statusCode(), response.body());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (IOException | InterruptedException e) {
            log.error("Erreur appel quote widget YellowCard: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Double fetchRateForLocalFallback(YellowCardWidgetQuoteRequest request) {
        try {
            boolean isBuy = "Buy".equalsIgnoreCase(
                    request.getTransactionType() != null ? request.getTransactionType().trim() : "Sell");
            ResponseEntity<String> ratesResponse = getRates(request.getCurrency());
            if (!ratesResponse.getStatusCode().is2xxSuccessful() || ratesResponse.getBody() == null) {
                return null;
            }
            BigDecimal rate = isBuy
                    ? parseYellowCardBuyRate(ratesResponse.getBody(), request.getCurrency())
                    : parseYellowCardSellRate(ratesResponse.getBody(), request.getCurrency());
            return rate.compareTo(BigDecimal.ZERO) > 0 ? rate.doubleValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ResponseEntity<String> getRatesByChannelId(String channelId, String currencyCode) {
        // Endpoint YellowCard: /business/rates/{channelId} ou /business/rates?channelId=...&currency=...
        // On essaie d'abord avec channelId dans le path, sinon en paramètre
        String url = baseUrl + "/rates";
        if (channelId != null && !channelId.isEmpty()) {
            url += "/" + channelId;
        }
        
        StringBuilder queryParams = new StringBuilder();
        if (currencyCode != null && !currencyCode.isEmpty()) {
            queryParams.append("currencyCode=").append(currencyCode);
        }
        if (queryParams.length() > 0) {
            url += "?" + queryParams;
        }

        String timestamp = Instant.now().toString();
        String endpointPath = channelId != null && !channelId.isEmpty() 
            ? "/business/rates/" + channelId 
            : "/business/rates";
        
        String authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                endpointPath, timestamp);

        HttpClient httpClient = createHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader)
                .header("X-YC-Timestamp", timestamp)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.debug("Rates récupérés avec succès pour channelId: {}, currency: {}", channelId, currencyCode);
                return ResponseEntity.ok(response.body());
            } else {
                log.warn("Erreur lors de l'appel à l'API YellowCard avec channelId: Code retour {} pour channelId: {}, currency: {}", 
                        response.statusCode(), channelId, currencyCode);
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Erreur interne lors de la récupération des rates avec channelId: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<String> getNetworks(String countryCode) {
        String url = baseUrl + "/networks";
        if (countryCode != null) {
            url += "?country=" + countryCode;
        }

        String timestamp = Instant.now().toString();

        String authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                "/business/networks", timestamp);

        HttpClient httpClient = createHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authorizationHeader)
                .header("X-YC-Timestamp", timestamp)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return ResponseEntity.ok(response.body());
            } else {
                log.error("Erreur lors de l'appel à l'API YellowCard: Code retour {}", response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Erreur interne: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<Object> estimateOnRampFees(Double amount, String currency, String countryCode, String channelId) {
        log.info("Attempting to estimate OnRamp fees from YellowCard API for amount: {} {}, country: {}, channelId: {}", 
                amount, currency, countryCode, channelId);

        // Liste des endpoints possibles à tester
        String[] endpointsToTry = {
            "/quote",
            "/pricing",
            "/estimate",
            "/collections/estimate"
        };

        String timestamp = Instant.now().toString();
        HttpClient httpClient = createHttpClient();

        for (String endpoint : endpointsToTry) {
            try {
                String url = baseUrl + endpoint;
                log.info("Tentative d'estimation via endpoint: {}", endpoint);

                // Pour les endpoints GET
                if (endpoint.equals("/quote") || endpoint.equals("/pricing") || endpoint.equals("/estimate")) {
                    // Construire les paramètres de requête
                    StringBuilder queryParams = new StringBuilder();
                    if (amount != null) queryParams.append("amount=").append(amount);
                    if (currency != null) {
                        if (queryParams.length() > 0) queryParams.append("&");
                        queryParams.append("currency=").append(currency);
                    }
                    if (countryCode != null) {
                        if (queryParams.length() > 0) queryParams.append("&");
                        queryParams.append("country=").append(countryCode);
                    }
                    if (channelId != null) {
                        if (queryParams.length() > 0) queryParams.append("&");
                        queryParams.append("channelId=").append(channelId);
                    }
                    if (queryParams.length() > 0) {
                        url += "?" + queryParams;
                    }

                    String authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
                            "/business" + endpoint, timestamp);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", authorizationHeader)
                            .header("X-YC-Timestamp", timestamp)
                            .header("Content-Type", "application/json")
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        log.info("✅ Succès avec endpoint '{}' - Réponse: {}", endpoint, response.body());
                        try {
                            JsonNode node = objectMapper.readTree(response.body());
                            return ResponseEntity.ok(node);
                        } catch (Exception e) {
                            log.warn("Réponse non JSON pour endpoint '{}': {}", endpoint, e.getMessage());
                            return ResponseEntity.ok(response.body());
                        }
                    } else if (response.statusCode() == 404) {
                        log.warn("❌ Endpoint '{}' retourne 404, tentative avec le suivant...", endpoint);
                        continue;
                    } else {
                        log.warn("⚠️ Endpoint '{}' retourne {}, tentative avec le suivant...", endpoint, response.statusCode());
                        continue;
                    }
                }
                // Pour les endpoints POST (comme /collections/estimate)
                else if (endpoint.equals("/collections/estimate")) {
                    // Construire un body minimal pour l'estimation
                    Map<String, Object> requestBodyMap = new LinkedHashMap<>();
                    if (channelId != null) requestBodyMap.put("channelId", channelId);
                    if (amount != null) requestBodyMap.put("localAmount", amount);
                    if (currency != null) requestBodyMap.put("currency", currency);
                    if (countryCode != null) requestBodyMap.put("country", countryCode);
                    requestBodyMap.put("customerType", CUSTOMER_TYPE);

                    String requestBody = objectMapper.writeValueAsString(requestBodyMap);
                    String authorizationHeader = generateAuthorizationHeaderWithBody(
                            apiKey, apiSecret, "/business" + endpoint, "POST", timestamp, requestBody);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", authorizationHeader)
                            .header("X-YC-Timestamp", timestamp)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        log.info("✅ Succès avec endpoint '{}' - Réponse: {}", endpoint, response.body());
                        try {
                            JsonNode node = objectMapper.readTree(response.body());
                            return ResponseEntity.ok(node);
                        } catch (Exception e) {
                            log.warn("Réponse non JSON pour endpoint '{}': {}", endpoint, e.getMessage());
                            return ResponseEntity.ok(response.body());
                        }
                    } else if (response.statusCode() == 404) {
                        log.warn("❌ Endpoint '{}' retourne 404, tentative avec le suivant...", endpoint);
                        continue;
                    } else {
                        log.warn("⚠️ Endpoint '{}' retourne {}, tentative avec le suivant...", endpoint, response.statusCode());
                        continue;
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Exception avec endpoint '{}': {}, tentative avec le suivant...", endpoint, e.getMessage());
                continue;
            }
        }

        log.error("❌ Tous les endpoints d'estimation ont échoué. Endpoints testés: {}", Arrays.toString(endpointsToTry));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @Override
    public ResponseEntity<Object> createWebPaymentCollection(WebPaymentInitRequest request,
                                                             String creatorWalletAddress,
                                                             String creatorUserId) {
        if (request == null) {
            return new ResponseEntity<>("WebPaymentInitRequest ne peut pas être null", HttpStatus.BAD_REQUEST);
        }
        log.info("Creating web payment collection for payment link: {} by payer: {}", 
                request.getUniqueCode(), request.getPhoneNumber());

        String sequenceId = generateSequenceId();
        String url = baseUrl + "/collections";
        String timestamp = Instant.now().toString();

        // ⚠️ IMPORTANT : Utiliser le taux YellowCard buy directement (pas Currency Freaks)
        // Cela garantit la cohérence avec le calcul des frais qui utilise aussi le taux YellowCard
        // Conversion : USD = XOF / Taux_YellowCard_buy
        BigDecimal convertedAmountUSD = getConvertedAmountUsingYellowCardBuyRate(
                request.getCurrency(), request.getAmount(), request.getCountryCode());
        if (convertedAmountUSD.compareTo(BigDecimal.ONE) < 0) {
            return new ResponseEntity<>("Le montant à transférer en USD doit être supérieur ou égal à 1. Montant converti: " + 
                    convertedAmountUSD.toString(), HttpStatus.BAD_REQUEST);
        }

        // Récupérer un channelId pour le pays (utiliser le premier channel disponible)
        String channelId = getFirstChannelIdForCountry(request.getCountryCode());
        if (channelId == null) {
            log.warn("Aucun channel trouvé pour le pays {}, utilisation d'un channel par défaut", request.getCountryCode());
            // Vous pouvez définir un channel par défaut dans les propriétés si nécessaire
            channelId = ""; // À remplacer par un channel par défaut si disponible
        }

        // Construire le OnRampRequest à partir de WebPaymentInitRequest
        OnRampRequest onRampRequest = buildOnRampRequestFromWebPayment(request, channelId, creatorWalletAddress, creatorUserId);

        // 🔄 Résolution automatique du channelId basée sur le networkId
        if (onRampRequest.getSource() != null && onRampRequest.getSource().getNetworkId() != null) {
            String originalChannelId = onRampRequest.getChannelId();
            String networkId = onRampRequest.getSource().getNetworkId();
            String resolvedChannelId = resolveChannelIdForNetwork(
                    originalChannelId, 
                    networkId, 
                    request.getCountryCode(), 
                    RAMP_TYPE_ON_RAMP
            );
            
            if (!resolvedChannelId.equals(originalChannelId)) {
                log.info("🔄 Remplacement du channelId pour WebPayment: {} → {} (networkId: {}, country: {})", 
                        originalChannelId, resolvedChannelId, networkId, request.getCountryCode());
                onRampRequest.setChannelId(resolvedChannelId);
            }
        }

        // Construire le body JSON
        // IMPORTANT: Envoyer la valeur brute en USD à YellowCard (ex: 3.532807416069328)
        String requestBody = buildRequestBody(onRampRequest, sequenceId, creatorWalletAddress, creatorUserId, convertedAmountUSD);

        String authorizationHeader = generateAuthorizationHeaderWithBody(
                apiKey, apiSecret, "/business/collections", "POST", timestamp, requestBody
        );

        // Appel API YellowCard
        var response = getObjectResponseEntity(url, requestBody, authorizationHeader, timestamp);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            String message = "Erreur lors de l'appel à l'API YellowCard: " + response.getBody();
            log.error(message);
            log.error("📤 Request body envoyé à YellowCard (WebPayment): {}", requestBody);
            return new ResponseEntity<>(message, response.getStatusCode());
        }

        // Extraire le redirectUrl de la réponse YellowCard
        String redirectUrl = extractRedirectUrlFromResponse(response.getBody());
        
        log.info("Web payment collection created successfully. Redirect URL: {}", redirectUrl);
        
        // Extraire bankInfo si présent dans la réponse YellowCard (paiement par virement bancaire)
        Map<String, Object> bankInfo = extractBankInfoFromResponse(response.getBody());

        // Retourner la réponse avec le redirectUrl
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", true);
        responseMap.put("redirectUrl", redirectUrl);
        responseMap.put("transactionId", sequenceId);
        responseMap.put("uniqueCode", request.getUniqueCode());
        responseMap.put("amount", request.getAmount());
        responseMap.put("currency", request.getCurrency());

        if (bankInfo != null) {
            responseMap.put("bankInfo", bankInfo.get("bankInfo"));
            responseMap.put("reference", bankInfo.get("reference"));
            responseMap.put("expiresAt", bankInfo.get("expiresAt"));
            responseMap.put("status", bankInfo.get("status") != null ? bankInfo.get("status") : "pending");
            log.info("📋 Web payment avec bankInfo pour lien: {}", request.getUniqueCode());
        }
        
        return ResponseEntity.status(response.getStatusCode()).body(responseMap);
    }

    /**
     * Construit un OnRampRequest à partir d'un WebPaymentInitRequest
     */
    private OnRampRequest buildOnRampRequestFromWebPayment(WebPaymentInitRequest webRequest, 
                                                           String channelId,
                                                           String creatorWalletAddress,
                                                           String creatorUserId) {
        OnRampRequest onRampRequest = new OnRampRequest();
        
        // Recipient : informations du créateur du lien (celui qui reçoit le paiement)
        RecipientDto recipient = new RecipientDto();
        // Pour les paiements web, on utilise les infos minimales
        // Le recipient est le créateur, mais on peut utiliser les infos du payeur si nécessaire
        recipient.setPhone(webRequest.getPhoneNumber()); // Utiliser le phone du payeur pour l'identification
        recipient.setCountry(webRequest.getCountryCode());
        recipient.setName(webRequest.getPayerName() != null ? webRequest.getPayerName() : "");
        recipient.setEmail(webRequest.getPayerEmail() != null ? webRequest.getPayerEmail() : "");
        recipient.setAddress(webRequest.getPayerAddress() != null ? webRequest.getPayerAddress() : "");
        recipient.setDob(webRequest.getPayerDob() != null ? webRequest.getPayerDob() : "");
        recipient.setIdNumber("");
        recipient.setIdType("");
        recipient.setAdditionalIdType("");
        recipient.setAdditionalIdNumber("");
        onRampRequest.setRecipient(recipient);

        // Source : informations du payeur (compte mobile money)
        SourceDto source = new SourceDto();
        source.setAccountNumber(webRequest.getPhoneNumber());
        source.setAccountType("momo");
        source.setNetworkId(webRequest.getOperatorId());
        source.setAccountName(webRequest.getPayerName() != null ? webRequest.getPayerName() : "");
        source.setPhoneNumber(webRequest.getPhoneNumber());
        onRampRequest.setSource(source);

        // Autres champs
        onRampRequest.setAmount(webRequest.getAmount());
        onRampRequest.setCurrency(webRequest.getCurrency());
        onRampRequest.setCountry(webRequest.getCountryCode());
        onRampRequest.setReason("other");
        onRampRequest.setChannelId(channelId);
        onRampRequest.setForceAccept(true);
        onRampRequest.setDirectSettlement(true);
        onRampRequest.setRampType(RAMP_TYPE_ON_RAMP);

        return onRampRequest;
    }

    /**
     * Récupère le premier channelId disponible pour un pays
     */
    private String getFirstChannelIdForCountry(String countryCode) {
        try {
            ResponseEntity<String> channelsResponse = getChannels(countryCode);
            if (channelsResponse.getStatusCode().is2xxSuccessful() && channelsResponse.getBody() != null) {
                JsonNode rootNode = objectMapper.readTree(channelsResponse.getBody());
                
                // Handle wrapped format: {"channels": [...]} or {"data": [...]} or direct array [...]
                JsonNode channelsArray;
                if (rootNode.isArray()) {
                    channelsArray = rootNode;
                } else if (rootNode.has("channels") && rootNode.get("channels").isArray()) {
                    channelsArray = rootNode.get("channels");
                } else if (rootNode.has("data") && rootNode.get("data").isArray()) {
                    channelsArray = rootNode.get("data");
                } else {
                    log.warn("Format inattendu pour /channels response");
                    return null;
                }
                
                if (channelsArray.size() > 0) {
                    JsonNode firstChannel = channelsArray.get(0);
                    if (firstChannel.has("id")) {
                        return firstChannel.get("id").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des channels pour le pays {}: {}", countryCode, e.getMessage());
        }
        return null;
    }

    /**
     * Extrait le redirectUrl de la réponse YellowCard
     */
    private String extractRedirectUrlFromResponse(Object responseBody) {
        try {
            if (responseBody == null) {
                return null;
            }
            
            String bodyString = responseBody instanceof String 
                    ? (String) responseBody 
                    : objectMapper.writeValueAsString(responseBody);
            
            JsonNode jsonNode = objectMapper.readTree(bodyString);
            if (jsonNode.has("redirectUrl")) {
                return jsonNode.get("redirectUrl").asText();
            }
            if (jsonNode.has("redirect_url")) {
                return jsonNode.get("redirect_url").asText();
            }
            if (jsonNode.has("url")) {
                return jsonNode.get("url").asText();
            }
        } catch (Exception e) {
            log.warn("Erreur lors de l'extraction du redirectUrl: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extrait les informations bancaires (bankInfo) de la réponse YellowCard.
     * Utilisé quand le mode de paiement est un virement bancaire.
     *
     * @param responseBody La réponse de YellowCard (JsonNode ou String)
     * @return Map contenant bankInfo, reference, expiresAt, status — ou null si bankInfo absent
     */
    private Map<String, Object> extractBankInfoFromResponse(Object responseBody) {
        try {
            if (responseBody == null) {
                return null;
            }

            JsonNode rootNode;
            if (responseBody instanceof JsonNode) {
                rootNode = (JsonNode) responseBody;
            } else if (responseBody instanceof String) {
                rootNode = objectMapper.readTree((String) responseBody);
            } else {
                String bodyString = objectMapper.writeValueAsString(responseBody);
                rootNode = objectMapper.readTree(bodyString);
            }

            if (!rootNode.has("bankInfo")) {
                return null;
            }

            JsonNode bankInfoNode = rootNode.get("bankInfo");
            Map<String, Object> bankDetails = new LinkedHashMap<>();
            if (bankInfoNode.has("accountNumber")) {
                bankDetails.put("accountNumber", bankInfoNode.get("accountNumber").asText());
            }
            if (bankInfoNode.has("name")) {
                bankDetails.put("name", bankInfoNode.get("name").asText());
            }
            if (bankInfoNode.has("accountName")) {
                bankDetails.put("accountName", bankInfoNode.get("accountName").asText());
            }
            // Inclure tout autre champ présent dans bankInfo
            bankInfoNode.fields().forEachRemaining(entry -> {
                if (!bankDetails.containsKey(entry.getKey())) {
                    JsonNode valueNode = entry.getValue();
                    if (valueNode.isTextual()) {
                        bankDetails.put(entry.getKey(), valueNode.asText());
                    } else if (valueNode.isNumber()) {
                        bankDetails.put(entry.getKey(), valueNode.numberValue());
                    } else if (valueNode.isBoolean()) {
                        bankDetails.put(entry.getKey(), valueNode.booleanValue());
                    } else {
                        try {
                            bankDetails.put(entry.getKey(), objectMapper.treeToValue(valueNode, Object.class));
                        } catch (Exception ex) {
                            bankDetails.put(entry.getKey(), valueNode.toString());
                        }
                    }
                }
            });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bankInfo", bankDetails);

            if (rootNode.has("reference")) {
                result.put("reference", rootNode.get("reference").asText());
            }
            if (rootNode.has("expiresAt")) {
                result.put("expiresAt", rootNode.get("expiresAt").asText());
            }
            if (rootNode.has("status")) {
                result.put("status", rootNode.get("status").asText());
            }

            log.info("✅ BankInfo extrait de la réponse YellowCard: {}", bankDetails);
            return result;

        } catch (Exception e) {
            log.warn("Erreur lors de l'extraction de bankInfo: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Utilise un mapping statique en configuration — ZERO appel API, O(1).
     * 
     * @param originalChannelId Le channelId envoyé par le frontend
     * @param networkId L'identifiant du réseau mobile money sélectionné
     * @param countryCode Le code pays (ex: "CI", "BJ")
     * @param rampType Le type de rampe ("deposit" ou "withdraw")
     * @return Le channelId correct basé sur le mapping statique, ou le channelId original si pas de mapping
     */
    private String resolveChannelIdForNetwork(String originalChannelId, String networkId, 
                                             String countryCode, String rampType) {
        if (networkId == null || networkId.isEmpty()) {
            log.debug("NetworkId non fourni, conservation du channelId original: {}", originalChannelId);
            return originalChannelId;
        }

        String resolvedChannelId = channelConfig.resolve(countryCode, rampType, networkId);
        
        if (resolvedChannelId != null) {
            if (!resolvedChannelId.equals(originalChannelId)) {
                log.info("🔄 Remplacement du channelId: {} → {} (networkId: {}, country: {}, rampType: {})", 
                        originalChannelId, resolvedChannelId, networkId, countryCode, rampType);
            } else {
                log.debug("✓ ChannelId original {} déjà correct pour networkId: {}", originalChannelId, networkId);
            }
            return resolvedChannelId;
        }
        
        // No override configured for this network — keep original
        log.debug("Pas de mapping configuré pour networkId: {} (country: {}, rampType: {}), conservation du channelId original: {}", 
                networkId, countryCode, rampType, originalChannelId);
        return originalChannelId;
    }

    // -----------------------
    // Méthodes privées
    // -----------------------

    /**
     * Vérifie le solde utilisateur en USDC.
     * ⚠️ IMPORTANT : Le wallet stocke le solde en USDC, donc on compare directement en USDC
     * 
     * @param username Le nom d'utilisateur
     * @param request La requête OffRamp
     * @param amountInUSD Le montant converti en USD (ce qui sera retiré du wallet en USDC)
     */
    private ResponseEntity<Object> verifyUserBalance(String username, OffRampRequest request, BigDecimal amountInUSD) {
        var walletBalance = walletService.getWalletBalance(username);

        if (!walletBalance.getStatusCode().is2xxSuccessful()) {
            log.debug("Erreur lors de la récupération du solde du wallet via Venly");
            return error((HttpStatus) walletBalance.getStatusCode(),
                    "Échec lors de la récupération du solde du wallet",
                    walletBalance.getBody());
        }

        if (walletBalance.getBody() == null) {
            return error((HttpStatus) walletBalance.getStatusCode());
        }

        double balanceUSDC;
        try {
            balanceUSDC = Double.parseDouble(walletBalance.getBody());
        } catch (Exception e) {
            log.warn("Impossible de parser le solde retourné: {} - {}", walletBalance.getBody(), e.getMessage());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Solde wallet invalide", walletBalance.getBody());
        }

        // ⚠️ IMPORTANT : Comparer directement en USDC
        // Le wallet stocke le solde en USDC, et amountInUSD est déjà converti en USD
        double amountUSD = amountInUSD != null ? amountInUSD.doubleValue() : 0.0;
        
        if (balanceUSDC < amountUSD) {
            double requestedAmount = request.getSettlementInfo().getCryptoAmount();
            String currency = request.getCurrency();
            
            // Convertir le solde en devise locale pour l'affichage
            double convertedBalance = getConvertedAmount("USDC", currency, balanceUSDC);
            
            log.error("❌ Solde insuffisant: {} USDC < {} USDC (demandé: {} {})", 
                    balanceUSDC, amountUSD, requestedAmount, currency);
            
            return error(HttpStatus.EXPECTATION_FAILED,
                    "Solde du wallet insuffisant",
                    "Le solde est de : " + balanceUSDC + " USDC (≈ " + convertedBalance + " " + currency + ")" +
                    " (montant demandé: " + requestedAmount + " " + currency + 
                    " ≈ " + amountUSD + " USDC)");
        }

        log.info("✅ Solde suffisant: {} USDC >= {} USDC", balanceUSDC, amountUSD);
        return ResponseEntity.ok().build();
    }

    /**
     * Vérifie que l'utilisateur a suffisamment de gas (MATIC) pour exécuter la transaction
     * ⚠️ CRITIQUE : Sans gas, la transaction échouera avec une erreur 403 Forbidden
     * ⚠️ IMPORTANT : Récupère le solde de gas depuis Venly et met à jour la base de données locale
     * 
     * @param username Le nom d'utilisateur
     * @return ResponseEntity avec le statut de la vérification
     */
    private ResponseEntity<Object> verifyGasBalance(String username) {
        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            log.error("Utilisateur non trouvé pour vérification du gas: {}", username);
            return error(HttpStatus.NOT_FOUND, "Utilisateur non trouvé", null);
        }
        
        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet == null) {
            log.error("Wallet non trouvé pour vérification du gas: {}", username);
            return error(HttpStatus.NOT_FOUND, "Wallet non trouvé", null);
        }
        
        // 1️⃣ D'abord vérifier la BD locale
        double gasBalance = wallet.getGasBalance();
        log.info("🔍 Solde de gas en BD locale: {} MATIC", gasBalance);
        
        // 2️⃣ Si la valeur en BD locale est 0, alors vérifier les deux champs (gasBalance et balance) depuis Venly
        if (gasBalance == 0) {
            try {
                log.info("🔄 Solde de gas en BD locale = 0, récupération depuis Venly pour le wallet: {}", wallet.getId());
                var gasBalanceResponse = walletClientService.getWalletGasBalance(wallet.getId());
                
                if (gasBalanceResponse.getStatusCode().is2xxSuccessful() && 
                    gasBalanceResponse.getBody() != null &&
                    gasBalanceResponse.getBody().getResult() != null) {
                    
                    var result = gasBalanceResponse.getBody().getResult();
                    
                    // Utiliser gasBalance en priorité, sinon utiliser balance comme fallback
                    if (result.getGasBalance() > 0) {
                        gasBalance = result.getGasBalance();
                        log.info("✅ Solde de gas récupéré depuis Venly (champ gasBalance): {} MATIC", gasBalance);
                    } else if (result.getBalance() > 0) {
                        gasBalance = result.getBalance();
                        log.info("✅ Solde de gas récupéré depuis Venly (champ balance, fallback): {} MATIC", gasBalance);
                    } else {
                        log.warn("⚠️ Les champs gasBalance et balance sont tous les deux à 0 dans la réponse Venly");
                    }
                    
                    // Mettre à jour la base de données locale avec la valeur récupérée depuis Venly
                    if (gasBalance > 0) {
                        wallet.setGasBalance(gasBalance);
                        walletRepository.save(wallet);
                        log.info("✅ Solde de gas mis à jour en BD: {} MATIC", gasBalance);
                    }
                } else {
                    log.warn("⚠️ Impossible de récupérer le solde de gas depuis Venly");
                }
            } catch (Exception e) {
                log.error("❌ Erreur lors de la récupération du solde de gas depuis Venly", e);
            }
        } else {
            log.info("✅ Utilisation du solde de gas de la BD locale (valeur > 0): {} MATIC", gasBalance);
        }
        
        // Minimum requis : 0.01 MATIC pour une transaction (peut varier selon la congestion du réseau)
        double minimumGasRequired = 0.01;
        
        log.info("🔍 Vérification du solde de gas: {} MATIC (minimum requis: {} MATIC)", 
                gasBalance, minimumGasRequired);
        
        if (gasBalance < minimumGasRequired) {
            log.error("❌ Solde de gas insuffisant: {} MATIC < {} MATIC (minimum requis)", 
                    gasBalance, minimumGasRequired);
            return error(HttpStatus.EXPECTATION_FAILED,
                    "Solde de gas insuffisant",
                    "Le solde de gas (MATIC) est insuffisant pour exécuter la transaction. " +
                    "Solde actuel: " + gasBalance + " MATIC, minimum requis: " + minimumGasRequired + " MATIC. " +
                    "Veuillez recharger votre wallet avec du MATIC pour pouvoir effectuer des transactions.");
        }
        
        log.info("✅ Solde de gas suffisant: {} MATIC >= {} MATIC", gasBalance, minimumGasRequired);
        return ResponseEntity.ok().build();
    }

    /**
     * Récupère le montant converti en USD avec la valeur brute (sans arrondi)
     * IMPORTANT: Retourne BigDecimal pour garder toute la précision (ex: 3.532807416069328)
     * Cette valeur brute doit être envoyée à YellowCard sans arrondi
     * 
     * ⚠️ DÉPRÉCIÉ : Utiliser getConvertedAmountUsingYellowCardBuyRate() ou getConvertedAmountUsingYellowCardSellRate()
     * Cette méthode utilise Currency Freaks, ce qui n'est plus cohérent avec le calcul des frais
     */
    @Deprecated
    private BigDecimal getConvertedAmountBigDecimal(String fromCurrency, String toCurrency, double balance) {
        var conversionResponse = freaksClientService.convertCurrency(fromCurrency, toCurrency, balance);

        if (conversionResponse.getStatusCode().is2xxSuccessful() &&
                conversionResponse.getBody() != null) {
            // Récupérer la valeur brute (String) et la convertir en BigDecimal pour garder toute la précision
            String convertedAmountStr = conversionResponse.getBody().getConvertedAmount();
            try {
                return new BigDecimal(convertedAmountStr);
            } catch (NumberFormatException e) {
                log.warn("Impossible de parser le montant converti: '{}', utilisation de 0.0", convertedAmountStr);
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Convertit un montant en devise locale vers USD en utilisant le taux YellowCard "buy" (pour OnRamp)
     * ⚠️ IMPORTANT : Utilise directement le taux YellowCard (pas Currency Freaks) pour être cohérent avec le calcul des frais
     * 
     * @param currency Devise locale (ex: "XOF")
     * @param amount Montant en devise locale
     * @param countryCode Code pays (optionnel, pour récupérer le channelId)
     * @return Montant converti en USD (BigDecimal avec toute la précision)
     */
    private BigDecimal getConvertedAmountUsingYellowCardBuyRate(String currency, double amount, String countryCode) {
        try {
            BigDecimal amountXOF = BigDecimal.valueOf(amount);
            
            // Récupérer le taux YellowCard buy
            String channelId = null;
            ResponseEntity<String> ratesResponse = null;
            
            if (countryCode != null && !countryCode.isEmpty()) {
                channelId = getFirstChannelIdForCountry(countryCode);
                if (channelId != null && !channelId.isEmpty()) {
                    log.debug("Récupération du taux YellowCard buy avec channelId: {} pour conversion OnRamp", channelId);
                    ratesResponse = getRatesByChannelId(channelId, currency);
                }
            }
            
            // Fallback si channelId n'est pas disponible
            if (ratesResponse == null || 
                ratesResponse.getStatusCode() != HttpStatus.OK || 
                ratesResponse.getBody() == null) {
                log.debug("Fallback: Récupération du taux YellowCard buy sans channelId pour conversion OnRamp");
                ratesResponse = getRates(currency);
            }
            
            if (ratesResponse.getStatusCode() == HttpStatus.OK && ratesResponse.getBody() != null) {
                BigDecimal buyRate = parseYellowCardBuyRate(ratesResponse.getBody(), currency);
                if (buyRate.compareTo(BigDecimal.ZERO) > 0) {
                    // Conversion : USD = XOF / Taux_YellowCard_buy
                    BigDecimal amountInUSD = amountXOF.divide(buyRate, 10, RoundingMode.HALF_UP);
                    log.info("✅ Conversion OnRamp via taux YellowCard buy : {} {} / {} = {} USD (valeur brute)", 
                            amountXOF, currency, buyRate, amountInUSD);
                    return amountInUSD;
                }
            }
            
            // Fallback vers Currency Freaks si le taux YellowCard n'est pas disponible
            log.warn("⚠️ Impossible de récupérer le taux YellowCard buy, utilisation de Currency Freaks en fallback");
            return getConvertedAmountBigDecimal(currency, "USD", amount);
            
        } catch (Exception e) {
            log.error("Erreur lors de la conversion via taux YellowCard buy, utilisation de Currency Freaks en fallback: {}", e.getMessage());
            return getConvertedAmountBigDecimal(currency, "USD", amount);
        }
    }

    /**
     * Convertit un montant en devise locale vers USD en utilisant le taux YellowCard "sell" (pour OffRamp)
     * ⚠️ IMPORTANT : Utilise directement le taux YellowCard (pas Currency Freaks) pour être cohérent avec le calcul des frais
     * 
     * @param currency Devise locale (ex: "XOF")
     * @param amount Montant en devise locale
     * @param countryCode Code pays (optionnel, pour récupérer le channelId)
     * @return Montant converti en USD (BigDecimal avec toute la précision)
     */
    private BigDecimal getConvertedAmountUsingYellowCardSellRate(String currency, double amount, String countryCode) {
        try {
            BigDecimal amountXOF = BigDecimal.valueOf(amount);
            
            // Récupérer le taux YellowCard sell
            String channelId = null;
            ResponseEntity<String> ratesResponse = null;
            
            if (countryCode != null && !countryCode.isEmpty()) {
                channelId = getFirstChannelIdForCountry(countryCode);
                if (channelId != null && !channelId.isEmpty()) {
                    log.debug("Récupération du taux YellowCard sell avec channelId: {} pour conversion OffRamp", channelId);
                    ratesResponse = getRatesByChannelId(channelId, currency);
                }
            }
            
            // Fallback si channelId n'est pas disponible
            if (ratesResponse == null || 
                ratesResponse.getStatusCode() != HttpStatus.OK || 
                ratesResponse.getBody() == null) {
                log.debug("Fallback: Récupération du taux YellowCard sell sans channelId pour conversion OffRamp");
                ratesResponse = getRates(currency);
            }
            
            if (ratesResponse.getStatusCode() == HttpStatus.OK && ratesResponse.getBody() != null) {
                BigDecimal sellRate = parseYellowCardSellRate(ratesResponse.getBody(), currency);
                if (sellRate.compareTo(BigDecimal.ZERO) > 0) {
                    // Conversion : USD = XOF / Taux_YellowCard_sell
                    BigDecimal amountInUSD = amountXOF.divide(sellRate, 10, RoundingMode.HALF_UP);
                    log.info("✅ Conversion OffRamp via taux YellowCard sell : {} {} / {} = {} USD (valeur brute)", 
                            amountXOF, currency, sellRate, amountInUSD);
                    return amountInUSD;
                }
            }
            
            // Fallback vers Currency Freaks si le taux YellowCard n'est pas disponible
            log.warn("⚠️ Impossible de récupérer le taux YellowCard sell, utilisation de Currency Freaks en fallback");
            return getConvertedAmountBigDecimal(currency, "USD", amount);
            
        } catch (Exception e) {
            log.error("Erreur lors de la conversion via taux YellowCard sell, utilisation de Currency Freaks en fallback: {}", e.getMessage());
            return getConvertedAmountBigDecimal(currency, "USD", amount);
        }
    }

    /**
     * Parse le taux YellowCard "sell" depuis la réponse JSON
     */
    private BigDecimal parseYellowCardSellRate(String jsonResponse, String currency) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Format YellowCard: {"rates": [{"buy": 588.96, "sell": 554.98, "code": "XOF", ...}, ...]}
            // ou format direct: {"buy": 588.96, "sell": 554.98, "code": "XOF", ...}
            if (root.has("rates") && root.get("rates").isArray()) {
                for (JsonNode rateItem : root.get("rates")) {
                    if (rateItem.has("code") && rateItem.get("code").asText().equals(currency)) {
                        if (rateItem.has("sell")) {
                            return new BigDecimal(rateItem.get("sell").asText());
                        }
                        if (rateItem.has("buy")) {
                            log.warn("Taux 'sell' non trouvé pour {}, utilisation du taux 'buy' comme fallback", currency);
                            return new BigDecimal(rateItem.get("buy").asText());
                        }
                    }
                }
            }

            // Format direct (objet unique)
            if (root.has("code") && root.get("code").asText().equals(currency)) {
                if (root.has("sell")) {
                    return new BigDecimal(root.get("sell").asText());
                }
                if (root.has("buy")) {
                    log.warn("Taux 'sell' non trouvé pour {}, utilisation du taux 'buy' comme fallback", currency);
                    return new BigDecimal(root.get("buy").asText());
                }
            }

            log.warn("Impossible de trouver le taux YellowCard 'sell' pour {} dans la réponse", currency);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Erreur lors du parsing du taux YellowCard 'sell': {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * @deprecated Utiliser getConvertedAmountBigDecimal pour garder la précision
     * Cette méthode est conservée pour compatibilité mais perd de la précision
     */
    @Deprecated
    private double getConvertedAmount(String fromCurrency, String toCurrency, double balance) {
        BigDecimal amount = getConvertedAmountBigDecimal(fromCurrency, toCurrency, balance);
        return amount.doubleValue(); // Perd de la précision, mais conservé pour compatibilité
    }

    // Dans YellowCardServiceImpl
    public HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    private ResponseEntity<Object> error(HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of("error", "Le montant du wallet est null"));
    }

    private ResponseEntity<Object> error(HttpStatus status, String message, Object details) {
        return ResponseEntity.status(status).body(Map.of(
                "error", message,
                "details", details
        ));
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid number format: '{}', using default {}", value, 0.0);
            return 0.0;
        }
    }

    /**
     * Construction du corps JSON pour OffRamp selon les spécifications YellowCard
     * Ordre des champs : sender, destination, channelId, sequenceId, currency, country, reason,
     * forceAccept, directSettlement, customerUID, customerType, settlementInfo
     */
    /**
     * Construit le body JSON pour OffRamp
     * @param usdAmount Montant converti en USD (valeur brute, ex: 3.532807416069328)
     *                   Cette valeur brute est envoyée à YellowCard sans arrondi
     */
    private String buildOffRampRequestBody(OffRampRequest request, String sequenceId,
                                           String customerUID, BigDecimal usdAmount) {

        if (request == null) {
            throw new IllegalArgumentException("OffRampRequest ne peut pas être null");
        }

        // 1. Construire sender selon l'ordre recommandé par YellowCard
        Map<String, Object> sender = new LinkedHashMap<>();
        if (request.getSender() != null) {
            safePut(sender, "name", request.getSender().getName());
            
            // ⚠️ CRITIQUE : Le champ "country" est OBLIGATOIRE dans sender pour YellowCard
            // Priorité : 1) sender.country, 2) request.country (racine), 3) chaîne vide
            String senderCountry = request.getSender().getCountry();
            if (senderCountry == null || senderCountry.trim().isEmpty()) {
                senderCountry = request.getCountry();
                if (senderCountry != null && !senderCountry.trim().isEmpty()) {
                    log.debug("Country manquant dans sender, utilisation du country racine: {}", senderCountry);
                } else {
                    log.warn("⚠️ Country manquant à la fois dans sender et au niveau racine");
                    senderCountry = "";
                }
            }
            // ⚠️ CRITIQUE : Toujours mettre country dans sender (OBLIGATOIRE pour YellowCard)
            sender.put("country", senderCountry != null ? senderCountry.trim() : "");
            log.debug("Country dans sender: '{}' (depuis sender: {}, depuis racine: {})", 
                    senderCountry, request.getSender().getCountry(), request.getCountry());
            
            safePut(sender, "address", request.getSender().getAddress());
            safePut(sender, "dob", request.getSender().getDob());
            safePut(sender, "email", request.getSender().getEmail());
            safePut(sender, "idNumber", request.getSender().getIdNumber());
            safePut(sender, "idType", request.getSender().getIdType());
            safePut(sender, "phone", request.getSender().getPhone());
            safePut(sender, "additionalIdType", request.getSender().getAdditionalIdType());
            safePut(sender, "additionalIdNumber", request.getSender().getAdditionalIdNumber());
        } else {
            log.error("buildOffRampRequestBody: sender est null - requête invalide");
            throw new IllegalArgumentException("Sender est obligatoire pour OffRamp");
        }

        // 2. Construire destination selon l'ordre recommandé par YellowCard
         // 2. Construire destination selon l'ordre recommandé par YellowCard
        Map<String, Object> destination = new LinkedHashMap<>();
        if (request.getDestination() != null) {
            safePut(destination, "accountName", request.getDestination().getAccountName());
            safePut(destination, "accountNumber", request.getDestination().getAccountNumber());
            safePut(destination, "accountType", request.getDestination().getAccountType());
            safePut(destination, "networkId", request.getDestination().getNetworkId());
            // accountBank : Nom de la banque pour les réseaux "Manual Input" (SEPA, etc.)
            // Doc YellowCard : "allow your customer enter their bank name which is to be passed as accountBank"
            safePut(destination, "accountBank", request.getDestination().getAccountBank());
        } else {
            log.error("buildOffRampRequestBody: destination est null - requête invalide");
            throw new IllegalArgumentException("Destination est obligatoire pour OffRamp");
        }

        // 3. Construire settlementInfo selon l'ordre recommandé par YellowCard
        Map<String, Object> settlement = new LinkedHashMap<>();
        if (request.getSettlementInfo() != null) {
            safePut(settlement, "cryptoCurrency", request.getSettlementInfo().getCryptoCurrency());
            safePut(settlement, "cryptoNetwork", request.getSettlementInfo().getCryptoNetwork());
            // cryptoAmount doit toujours être présent
            // ⚠️ CRITIQUE : Envoyer la valeur BRUTE (toutes les décimales) à YellowCard
            // Exemple : Si conversion donne 3.6666666666 USD, on envoie 3.6666666666 (pas 3.67)
            // Si on arrondit à 2 décimales avant d'envoyer, on perd de la précision et on ne retrouve plus le montant initial
            // YellowCard traite lui-même les arrondis - on ne doit PAS arrondir avant l'envoi
            settlement.put("cryptoAmount", usdAmount);
        } else {
            log.error("buildOffRampRequestBody: settlementInfo est null - requête invalide");
            throw new IllegalArgumentException("SettlementInfo est obligatoire pour OffRamp");
        }

        // 4. Construire le payload principal selon l'ordre recommandé par YellowCard
        // Utiliser LinkedHashMap pour préserver l'ordre d'insertion
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender);
        payload.put("destination", destination);
        safePut(payload, "channelId", request.getChannelId());
        safePut(payload, "sequenceId", sequenceId);
        safePut(payload, "currency", request.getCurrency());
        
        // ⚠️ CRITIQUE : Le country au niveau racine doit aussi être présent
        // Utiliser request.getCountry() si disponible, sinon utiliser sender.country comme fallback
        String country = request.getCountry();
        if (country == null || country.isEmpty()) {
            // Récupérer le country depuis sender (déjà traité ci-dessus)
            Object senderCountryObj = sender.get("country");
            country = senderCountryObj != null ? senderCountryObj.toString() : null;
            if (country != null && !country.isEmpty()) {
                log.debug("Country manquant au niveau racine, utilisation de sender.country: {}", country);
            }
        }
        
        // Validation : country doit être présent (soit au niveau racine, soit dans sender)
        if (country == null || country.isEmpty()) {
            log.error("❌ Country manquant - YellowCard rejettera la requête");
            throw new IllegalArgumentException("Le champ 'country' est obligatoire (doit être présent soit dans sender, soit au niveau racine)");
        }
        
        safePut(payload, "country", country);
        safePut(payload, "reason", request.getReason());
        payload.put("forceAccept", request.isForceAccept());
        payload.put("directSettlement", request.isDirectSettlement());
        safePut(payload, "customerUID", customerUID);
        safePut(payload, "customerType", CUSTOMER_TYPE);
        payload.put("settlementInfo", settlement);

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            log.info("📤 OffRamp request body sent to YellowCard: {}", jsonBody);
            
            // ⚠️ Vérification critique : s'assurer que country est présent dans sender
            @SuppressWarnings("unchecked")
            Map<String, Object> senderInPayload = (Map<String, Object>) payload.get("sender");
            if (senderInPayload != null) {
                Object countryInSender = senderInPayload.get("country");
                if (countryInSender == null || countryInSender.toString().trim().isEmpty()) {
                    log.error("❌ CRITIQUE : Country manquant dans sender après construction du payload !");
                } else {
                    log.info("✅ Country présent dans sender: '{}'", countryInSender);
                }
            }
            
            return jsonBody;
        } catch (JsonProcessingException e) {
            log.error("Erreur JSON OffRamp: {}", e.getMessage(), e);
            throw new IllegalStateException("Impossible de construire le corps JSON OffRamp", e);
        }
    }


    /**
     * Handler de la réponse OffRamp
     */
    private ResponseEntity<Object> handleOffRampResponse(
            ResponseEntity<Object> response,
            String username,
            String headerPin,
            String sequenceId,
            OffRampRequest request,
            BigDecimal convertedAmountUSD) {

        try {
            // ⚠️ CRITIQUE : Vérifier que le headerPin est présent avant de continuer
            if (headerPin == null || headerPin.isEmpty()) {
                log.error("❌ HeaderPin manquant ou vide dans handleOffRampResponse pour user {}", username);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "message", "HeaderPin manquant - impossible d'exécuter le transfert",
                                "transactionId", ""
                        ));
            }
            
            log.info("🔐 HeaderPin reçu pour user {} (présent: {}, longueur: {})", 
                    username, headerPin != null && !headerPin.isEmpty(), 
                    headerPin != null ? headerPin.length() : 0);
            
            // convertir la réponse en DTO OffRampResponse
            OffRampResponse dto = objectMapper.convertValue(response.getBody(), OffRampResponse.class);
            if (dto == null || dto.getSettlementInfo() == null) {
                log.error("Réponse OffRamp invalide ou incomplète (dto ou settlementInfo null)");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "message", "Réponse OffRamp invalide ou incomplète",
                                "transactionId", ""
                        ));
            }

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.error("Utilisateur introuvable: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "message", "Utilisateur non trouvé avec le username " + username,
                                "transactionId", ""
                        ));
            }

            // 1️⃣ Exécution du transfert crypto
            // ⚠️ IMPORTANT : Utiliser le montant converti en USD (convertedAmountUSD) pour le transfert
            // Le montant dans la réponse YellowCard peut être différent, on doit utiliser celui qu'on a calculé
            var transfertResponse = executeCryptoTransfert(user, dto, headerPin, convertedAmountUSD);
            if (transfertResponse.getStatusCode().is2xxSuccessful()) {
                // 2️⃣ Mise à jour du wallet après le paiement OffRamp
                // ⚠️ IMPORTANT : Passer le montant en USDC (convertedAmountUSD) pour mettre à jour le wallet
                // Le wallet blockchain retire des USDC, donc le wallet local doit aussi être mis à jour en USDC
                // ⚠️ IMPORTANT : Passer aussi le montant demandé par l'utilisateur pour débiter correctement le wallet
                // L'utilisateur doit recevoir exactement le montant demandé, comme dans OnRamp où le montant initial est crédité tel quel
                Wallet wallet = walletRepository.findByUsers(user);
                if (wallet != null) {
                    double requestedAmount = request.getSettlementInfo().getCryptoAmount();
                    updateWalletAfterOffRamp(wallet, request, sequenceId, convertedAmountUSD, requestedAmount);
                } else {
                    log.warn("Wallet introuvable pour l'utilisateur {} — mise à jour ignorée", username);
                }
                //TODO :  retourner id de la transaction en cas de succès pour que le front puis checker le status
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of(
                                "success", true,
                                "message", "Transfert réussi et wallet mis à jour",
                                "transactionId", sequenceId
                        ));
            } else {
                log.error("Erreur lors du transfert du montant {}, pour le user {} - Status: {}, Body: {}", 
                        safeValue(dto.getAmount()), username, 
                        transfertResponse.getStatusCode(), 
                        transfertResponse.getBody());
                String errorMessage = "Erreur lors du transfert";
                if (transfertResponse.getBody() != null) {
                    errorMessage += ": " + transfertResponse.getBody().toString();
                }
                return ResponseEntity.status(transfertResponse.getStatusCode())
                        .body(Map.of(
                                "success", false,
                                "message", errorMessage,
                                "transactionId", ""
                        ));
            }

        } catch (IllegalArgumentException e) {
            log.error("Erreur lors du mapping OffRampResponse: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Erreur lors du traitement de la réponse OffRamp: " + e.getMessage(),
                            "transactionId", ""
                    ));
        }
    }

    /**
     * Met à jour le wallet après un OffRamp (débit)
     * ⚠️ IMPORTANT : Comme dans OnRamp où le montant initial est crédité tel quel, ici l'utilisateur doit recevoir exactement le montant demandé
     * YellowCard reçoit l'équivalent exact du montant demandé, comme dans OnRamp où YellowCard reçoit l'équivalent exact du montant initial
     * 
     * @param wallet Le wallet à mettre à jour
     * @param request La requête OffRamp
     * @param sequenceId L'ID de séquence de l'opération
     * @param amountInUSD Le montant en USDC retiré du wallet blockchain (équivalent exact du montant demandé, comme dans OnRamp)
     * @param requestedAmount Le montant demandé par l'utilisateur (ce qu'il doit recevoir exactement)
     */
       /**
     * Met à jour le wallet après un OffRamp (débit)
     */
    private void updateWalletAfterOffRamp(Wallet wallet, OffRampRequest request, String sequenceId, BigDecimal amountInUSD, double requestedAmount) {
        if (wallet == null) return;

        double amountUSD = amountInUSD != null ? amountInUSD.doubleValue() : 0.0;

        double walletBalanceBefore = safeDouble(wallet.getBalance());
        double walletBalanceAfter = walletBalanceBefore - amountUSD;
        wallet.setBalance(walletBalanceAfter);

        double walletDeviseBalanceBefore = safeDouble(wallet.getDeviseBalance());
        double walletDeviseBalanceAfter = walletDeviseBalanceBefore - requestedAmount;
        wallet.setDeviseBalance(walletDeviseBalanceAfter);

        walletRepository.saveAndFlush(wallet);

        log.info("Wallet mis à jour après OffRamp : Balance USDC {} → {} (retrait: {} USDC), DeviseBalance {} → {} (retrait: {} {})",
                walletBalanceBefore, walletBalanceAfter, amountUSD,
                walletDeviseBalanceBefore, walletDeviseBalanceAfter, requestedAmount,
                request != null ? request.getCurrency() : "N/A");

        Operation operation = new Operation();
        operation.setAmount(requestedAmount);
        operation.setDevise(safeValue(request == null ? null : request.getCurrency()).toString());
        operation.setConvertedAmount((double) amountUSD);
        operation.setCreatedAt(LocalDateTime.now());
        operation.setUpdatedAt(LocalDateTime.now());
        operation.setStatus("PENDING");
        operation.setUsername(safeValue(request == null || request.getSender() == null ? null : request.getSender().getPhone()).toString());
        operation.setType("DEBIT");
        operation.setDesignation("YELLOWCARD_OFF_RAMP");
        operation.setTransactionType("Retrait");
        operation.setProvider("YellowCard");
        operation.setOperationHash(safeValue(sequenceId).toString());
        operation.setProviderAmount(amountUSD);
        operation.setProviderDevise("USDC");

        operationRepository.save(operation);
    }
    
    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    /**
     * Execute the crypto transfer using the transfertService
     * @param user L'utilisateur qui effectue le transfert
     * @param dto La réponse OffRamp de YellowCard
     * @param headerPin Le PIN d'authentification
     * @param convertedAmountUSD Le montant converti en USD à transférer (doit être utilisé au lieu de settlement.getCryptoAmount())
     */
    private ResponseEntity<Object> executeCryptoTransfert(Users user, OffRampResponse dto,
                                                          String headerPin, BigDecimal convertedAmountUSD) {
        var settlement = dto.getSettlementInfo();
        if (settlement == null) {
            log.error("SettlementInfo est null dans OffRampResponse pour user {}", user.getUsername());
            return new ResponseEntity<>("SettlementInfo manquant dans la réponse YellowCard", HttpStatus.BAD_REQUEST);
        }
        
        // ⚠️ IMPORTANT : Utiliser le montant converti en USD (convertedAmountUSD) au lieu de settlement.getCryptoAmount()
        // Le montant dans la réponse YellowCard peut être différent ou incorrect, on doit utiliser celui qu'on a calculé
        if (convertedAmountUSD == null || convertedAmountUSD.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("❌ Montant converti invalide ou nul: {} (doit être > 0)", convertedAmountUSD);
            return new ResponseEntity<>(Map.of(
                    "error", "Montant converti invalide: " + (convertedAmountUSD != null ? convertedAmountUSD : "null"),
                    "status", HttpStatus.BAD_REQUEST.value()
            ), HttpStatus.BAD_REQUEST);
        }
        
        double amount = convertedAmountUSD.doubleValue();
        String walletAddress = safeValue(settlement.getWalletAddress()).toString();
        String currency = safeValue(settlement.getCryptoCurrency()).toString();
        
        // Comparer le montant converti avec celui de la réponse YellowCard pour diagnostic
        double yellowCardAmount = settlement.getCryptoAmount();
        if (Math.abs(amount - yellowCardAmount) > 0.01) {
            log.warn("⚠️ Différence entre montant converti ({}) et montant YellowCard ({}): {}", 
                    amount, yellowCardAmount, Math.abs(amount - yellowCardAmount));
        }

        // ⚠️ CRITIQUE : Logger tous les paramètres avant l'appel au transfert pour diagnostic
        log.info("🔄 Exécution du transfert crypto OffRamp:");
        log.info("   - User: {} (userId: {})", user.getUsername(), user.getUserId());
        
        // Récupérer le wallet pour vérifier le gas balance une dernière fois
        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet != null) {
            log.info("   - Wallet Sender ID: {}", wallet.getId());
            log.info("   - Wallet Sender Address: {}", wallet.getAddress());
            log.info("   - Wallet Sender Balance: {} USDC", wallet.getBalance());
            log.info("   - Wallet Sender Gas Balance: {} MATIC", wallet.getGasBalance());
            
            // Vérification finale du gas balance juste avant le transfert
            double finalGasBalance = wallet.getGasBalance();
            double minimumGasRequired = 0.01;
            if (finalGasBalance < minimumGasRequired) {
                log.error("❌ Solde de gas insuffisant juste avant le transfert: {} MATIC < {} MATIC", 
                        finalGasBalance, minimumGasRequired);
                return new ResponseEntity<>(Map.of(
                        "error", "Solde de gas insuffisant",
                        "details", "Le solde de gas (MATIC) est insuffisant pour exécuter la transaction. " +
                                "Solde actuel: " + finalGasBalance + " MATIC, minimum requis: " + minimumGasRequired + " MATIC.",
                        "status", HttpStatus.EXPECTATION_FAILED.value()
                ), HttpStatus.EXPECTATION_FAILED);
            }
        }
        
        log.info("   - Wallet Address (destination): {}", walletAddress);
        log.info("   - Amount (USDC) - montant converti utilisé: {} (YellowCard: {})", amount, yellowCardAmount);
        log.info("   - Currency: {}", currency);
        log.info("   - HeaderPin: {} (présent: {})", 
                headerPin != null && !headerPin.isEmpty() ? "***" : "NULL/VIDE", 
                headerPin != null && !headerPin.isEmpty());
        
        // Vérifier que le headerPin est présent
        if (headerPin == null || headerPin.isEmpty()) {
            log.error("❌ HeaderPin manquant ou vide - le transfert échouera avec 403 Forbidden");
            return new ResponseEntity<>(Map.of(
                    "error", "HeaderPin manquant - impossible d'exécuter le transfert",
                    "status", HttpStatus.BAD_REQUEST.value()
            ), HttpStatus.BAD_REQUEST);
        }

        // Vérifier que le walletAddress est présent
        if (walletAddress == null || walletAddress.isEmpty() || "null".equals(walletAddress)) {
            log.error("❌ Wallet Address manquant ou invalide dans la réponse YellowCard");
            return new ResponseEntity<>(Map.of(
                    "error", "Wallet Address manquant dans la réponse YellowCard",
                    "status", HttpStatus.BAD_REQUEST.value()
            ), HttpStatus.BAD_REQUEST);
        }

        var transfertResponse = transfertService.executeOnRampTokenTransfert(
                user, walletAddress, amount, amount, headerPin, currency);

        if (transfertResponse.getStatusCode().is2xxSuccessful()) {
            log.info("✅ Transfert réussi pour {}", safeValue(user.getUsername()));
            return ResponseEntity.ok(Map.of("message", "Transfert réussi"));
        } else {
            // ⚠️ CRITIQUE : Logger l'erreur complète pour diagnostic
            log.error("❌ Échec du transfert pour l'utilisateur {} - Status: {}, Body: {}", 
                    safeValue(user.getUsername()), 
                    transfertResponse.getStatusCode(), 
                    transfertResponse.getBody());
            
            // Analyser le code d'erreur pour donner un message plus explicite
            String errorMessage = "Échec du transfert";
            String errorDetails = "";
            
            if (transfertResponse.getBody() != null) {
                errorDetails = transfertResponse.getBody().toString();
                errorMessage += ": " + errorDetails;
            }
            
            // Messages spécifiques selon le code d'erreur
            if (transfertResponse.getStatusCode() == HttpStatus.FORBIDDEN) {
                errorMessage = "Erreur 403 Forbidden - Vérifiez : " +
                        "1) Le PIN est correct et valide, " +
                        "2) L'utilisateur a les permissions nécessaires, " +
                        "3) Le solde du wallet est suffisant, " +
                        "4) Le wallet address est valide";
                if (!errorDetails.isEmpty()) {
                    errorMessage += " | Détails: " + errorDetails;
                }
            } else if (transfertResponse.getStatusCode() == HttpStatus.BAD_REQUEST) {
                errorMessage = "Erreur 400 Bad Request - Vérifiez les paramètres du transfert: " + errorDetails;
            } else if (transfertResponse.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                errorMessage = "Erreur 401 Unauthorized - Problème d'authentification (PIN invalide?)";
            }
            
            return new ResponseEntity<>(Map.of(
                    "error", errorMessage, 
                    "status", transfertResponse.getStatusCode().value(),
                    "details", errorDetails
            ), transfertResponse.getStatusCode());
        }
    }

    /**
     * Construction du corps JSON pour OnRamp selon les spécifications YellowCard
     * Ordre des champs : recipient, source, amount, currency, country, reason, sequenceId, 
     * channelId, customerUID, customerType, forceAccept, directSettlement, rampType, 
     * redirectUrl, settlementInfo
     */
    /**
     * Construit le body JSON pour OnRamp
     * @param usdAmount Montant converti en USD (valeur brute, ex: 3.532807416069328)
     *                   Cette valeur brute est envoyée à YellowCard sans arrondi
     */
    private String buildRequestBody(OnRampRequest request, String sequenceId, String walletAddress,
                                    String customerUID, BigDecimal usdAmount) {

        if (request == null) {
            throw new IllegalArgumentException("OnRampRequest ne peut pas être null");
        }

        // 1. Construire recipient selon l'ordre recommandé par YellowCard
        Map<String, Object> recipient = new LinkedHashMap<>();
        String resolvedCustomerType = (request.getCustomerType() != null && !request.getCustomerType().isBlank())
                ? request.getCustomerType() : CUSTOMER_TYPE;

        // AUTO-DÉTECTION : Si customerType résolu en "retail" mais que businessName est renseigné
        // et que name est vide, c'est en réalité une institution → forcer customerType à "institution"
        if (CUSTOMER_TYPE.equalsIgnoreCase(resolvedCustomerType)
                && request.getRecipient() != null
                && request.getRecipient().getBusinessName() != null
                && !request.getRecipient().getBusinessName().isBlank()
                && (request.getRecipient().getName() == null || request.getRecipient().getName().isBlank())) {
            resolvedCustomerType = CUSTOMER_TYPE_INSTITUTION;
            log.info("Auto-détection: customerType forcé à '{}' car businessName '{}' est renseigné et name est vide",
                    CUSTOMER_TYPE_INSTITUTION, request.getRecipient().getBusinessName());
        }

        if (request.getRecipient() != null) {
            if (CUSTOMER_TYPE_INSTITUTION.equalsIgnoreCase(resolvedCustomerType)) {
                safePut(recipient, "businessName", request.getRecipient().getBusinessName());
                safePut(recipient, "businessId", request.getRecipient().getBusinessId());
            } else {
                // Fallback: si name est vide mais businessName existe, utiliser businessName
                String recipientName = request.getRecipient().getName();
                if (recipientName == null || recipientName.isBlank()) {
                    recipientName = request.getRecipient().getBusinessName();
                    if (recipientName != null && !recipientName.isBlank()) {
                        log.info("Fallback: utilisation de businessName '{}' comme name pour recipient retail", recipientName);
                    }
                }
                safePut(recipient, "name", recipientName);

                // Fallback: si country est vide, utiliser le country au niveau racine de la requête
                String recipientCountry = request.getRecipient().getCountry();
                if (recipientCountry == null || recipientCountry.isBlank()) {
                    recipientCountry = request.getCountry();
                    if (recipientCountry != null && !recipientCountry.isBlank()) {
                        log.info("Fallback: utilisation du country racine '{}' comme country pour recipient", recipientCountry);
                    }
                }
                safePut(recipient, "country", recipientCountry);

                // Fallback: si phone est vide, utiliser source.phoneNumber ou source.accountNumber
                String recipientPhone = request.getRecipient().getPhone();
                if ((recipientPhone == null || recipientPhone.isBlank()) && request.getSource() != null) {
                    recipientPhone = request.getSource().getPhoneNumber();
                    if (recipientPhone == null || recipientPhone.isBlank()) {
                        recipientPhone = request.getSource().getAccountNumber();
                    }
                    if (recipientPhone != null && !recipientPhone.isBlank()) {
                        log.info("Fallback: utilisation du phone source '{}' comme phone pour recipient", recipientPhone);
                    }
                }
                safePut(recipient, "phone", recipientPhone);

                safePut(recipient, "address", request.getRecipient().getAddress());
                safePut(recipient, "email", request.getRecipient().getEmail());
                safePut(recipient, "dob", request.getRecipient().getDob());
                safePut(recipient, "idNumber", request.getRecipient().getIdNumber());
                safePut(recipient, "idType", request.getRecipient().getIdType());
                safePut(recipient, "additionalIdType", request.getRecipient().getAdditionalIdType());
                safePut(recipient, "additionalIdNumber", request.getRecipient().getAdditionalIdNumber());
            }
        } else {
            log.error("buildRequestBody: recipient est null - requête invalide");
            throw new IllegalArgumentException("Recipient est obligatoire pour OnRamp");
        }

        // 2. Construire source selon l'ordre recommandé par YellowCard
        Map<String, Object> source = new LinkedHashMap<>();
        if (request.getSource() != null) {
            safePut(source, "accountNumber", request.getSource().getAccountNumber());
            safePut(source, "accountType", request.getSource().getAccountType());
            safePut(source, "networkId", request.getSource().getNetworkId());
            safePut(source, "accountName", request.getSource().getAccountName());
            // phoneNumber : utiliser le champ phoneNumber s'il existe, sinon accountNumber comme fallback
            String phoneNumber = request.getSource().getPhoneNumber();
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                phoneNumber = request.getSource().getAccountNumber();
            }
            safePut(source, "phoneNumber", phoneNumber);
        } else {
            log.error("buildRequestBody: source est null - requête invalide");
            throw new IllegalArgumentException("Source est obligatoire pour OnRamp");
        }

        // 3. Construire settlementInfo selon l'ordre recommandé par YellowCard
        Map<String, Object> settlement = new LinkedHashMap<>();
        safePut(settlement, "cryptoCurrency", CRYPTO_CURRENCY);
        safePut(settlement, "cryptoNetwork", CRYPTO_NETWORK);
        safePut(settlement, "walletAddress", walletAddress);

        // 4. Construire le payload principal selon l'ordre recommandé par YellowCard
        // Utiliser LinkedHashMap pour préserver l'ordre d'insertion
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipient", recipient);
        payload.put("source", source);
        // ⚠️ CRITIQUE : Envoyer la valeur BRUTE (toutes les décimales) à YellowCard
        // Exemple : Si conversion donne 3.6666666666 USD, on envoie 3.6666666666 (pas 3.67)
        // Si on arrondit à 2 décimales avant d'envoyer, on perd de la précision et on ne retrouve plus le montant initial (ex: 2000 XOF)
        // YellowCard traite lui-même les arrondis - on ne doit PAS arrondir avant l'envoi
        // ObjectMapper sérialisera automatiquement BigDecimal avec toute sa précision
        // ⚠️ IMPORTANT : Ne PAS envoyer "localAmount" - YellowCard n'accepte que "amount" (en USD)
        // Le champ "localAmount" cause l'erreur "instance failed to match exactly one schema"
        payload.put("amount", usdAmount); // Montant en USD (valeur brute, toutes décimales préservées)
        // ⚠️ CRITIQUE : Le montant envoyé (usdAmount) est déjà converti en USD
        // La currency doit correspondre au montant envoyé → toujours "USD" pour OnRamp
        safePut(payload, "currency", "USD");
        safePut(payload, "country", request.getCountry());
        safePut(payload, "reason", request.getReason());
        safePut(payload, "sequenceId", sequenceId);
        safePut(payload, "channelId", request.getChannelId());
        safePut(payload, "customerUID", customerUID);
        safePut(payload, "customerType", resolvedCustomerType);
        payload.put("forceAccept", request.isForceAccept());
        payload.put("directSettlement", request.isDirectSettlement());
        safePut(payload, "rampType", RAMP_TYPE_ON_RAMP);
        safePut(payload, "redirectUrl", webPayBaseUrl);
        payload.put("settlementInfo", settlement);

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            log.info("OnRamp request body sent to YellowCard: {}", jsonBody);
            return jsonBody;
        } catch (JsonProcessingException e) {
            log.error("Erreur lors de la création du corps JSON OnRamp: {}", e.getMessage(), e);
            throw new IllegalStateException("Erreur lors de la création du corps JSON", e);
        }
    }

    /**
     * Construit la destination en normalisant les champs et en remplaçant null -> "".
     * @deprecated Cette méthode n'est plus utilisée pour OnRamp, remplacée par la logique inline dans buildRequestBody
     * Conservée pour l'OffRamp si nécessaire
     */
    @Deprecated
    private Map<String, Object> buildDestination(SourceDto source) {
        Map<String, Object> dest = new LinkedHashMap<>();
        if (source == null) {
            log.warn("buildDestination: source null, on renvoie map vide avec valeurs vides");
            safePut(dest, "accountNumber", null);
            safePut(dest, "accountType", null);
            safePut(dest, "networkId", null);
            safePut(dest, "accountName", null);
            safePut(dest, "phoneNumber", null);
            return dest;
        }

        String accountType = safeValue(source.getAccountType()).toString().toLowerCase();

        // Ordre selon les spécifications YellowCard : accountNumber, accountType, networkId, accountName, phoneNumber
        safePut(dest, "accountNumber", source.getAccountNumber());
        safePut(dest, "accountType", source.getAccountType());
        safePut(dest, "networkId", source.getNetworkId());
        safePut(dest, "accountName", source.getAccountName());
        // phoneNumber : utiliser le champ phoneNumber s'il existe, sinon accountNumber comme fallback
        String phoneNumber = source.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            phoneNumber = source.getAccountNumber();
        }
        safePut(dest, "phoneNumber", phoneNumber);

        if (!accountType.equals("momo") && !accountType.equals("bank")) {
            // On garde la map, mais on signale l'erreur
            throw new IllegalArgumentException("Type de compte non pris en charge: " + accountType);
        }
        return dest;
    }

        /**
     * Crée l'opération PENDING pour un OnRamp (dépôt) SANS créditer le wallet.
     * Le wallet sera crédité uniquement quand le webhook COMPLETED arrive
     * via creditWalletOnCompletedDeposit() dans YellowCardEventWebhookController.
     */
    private void createOnRampOperationPending(Wallet wallet, OnRampRequest request, String sequenceId, BigDecimal amountInUSD) {
        if (wallet == null || request == null) return;

        double amountUSD = amountInUSD != null ? amountInUSD.doubleValue() : 0.0;
        double amountLocalCurrency = request.getAmount();

        // ⚠️ NE PAS créditer le wallet ici — attendre le webhook COMPLETED
        // Le saveBuyRate peut être fait ici car c'est informatif
        saveBuyRateForWallet(wallet, request);
        walletRepository.saveAndFlush(wallet);

        log.info("📝 Opération OnRamp PENDING créée (wallet NON crédité) : {} {} = {} USDC, sequenceId={}",
                amountLocalCurrency, request.getCurrency(), amountUSD, sequenceId);

        Operation operation = new Operation();
        operation.setAmount(amountLocalCurrency);
        operation.setDevise(safeValue(request.getCurrency()).toString());
        operation.setConvertedAmount(amountUSD);
        operation.setCreatedAt(LocalDateTime.now());
        operation.setUpdatedAt(LocalDateTime.now());
        operation.setStatus("PENDING");
        operation.setDesignation("YELLOWCARD_ON_RAMP");
        operation.setTransactionType("Dépôt");
        operation.setProvider("YellowCard");
        operation.setType("CREDIT");
        operation.setOperationHash(safeValue(sequenceId).toString());
        operation.setProviderAmount(amountUSD);
        operation.setProviderDevise("USDC");
        operation.setUsername(wallet.getUsers() != null ? wallet.getUsers().getUsername() : null);

        operationRepository.save(operation);
    }
    
    /**
     * Sauvegarde le taux d'achat YellowCard dans le wallet pour l'affichage du solde
     * Selon la proposition de David : sauvegarder le taux lors du dépôt et l'utiliser jusqu'au prochain dépôt
     */
    private void saveBuyRateForWallet(Wallet wallet, OnRampRequest request) {
        try {
            String currency = request.getCurrency();
            String countryCode = request.getCountry();

            if (currency == null || countryCode == null) {
                log.warn("Currency ou countryCode manquant, impossible de sauvegarder le taux d'achat");
                return;
            }

            // Récupérer le channelId pour le pays
            String channelId = getFirstChannelIdForCountry(countryCode);

            // Récupérer les taux YellowCard avec channelId si disponible, sinon sans channelId
            ResponseEntity<String> ratesResponse;
            if (channelId != null && !channelId.isEmpty()) {
                log.debug("Récupération du taux YellowCard avec channelId: {}", channelId);
                ratesResponse = getRatesByChannelId(channelId, currency);
            } else {
                log.debug("Récupération du taux YellowCard sans channelId (fallback)");
                ratesResponse = getRates(currency);
            }

            if (ratesResponse.getStatusCode().is2xxSuccessful() && ratesResponse.getBody() != null) {
                BigDecimal buyRate = parseYellowCardBuyRate(ratesResponse.getBody(), currency);
                if (buyRate.compareTo(BigDecimal.ZERO) > 0) {
                    wallet.setLastBuyRate(buyRate.doubleValue());
                    wallet.setLastBuyRateUpdatedAt(LocalDateTime.now());
                    wallet.setLastProvider("yellowcard");
                    wallet.setLastBuyRateCurrency(currency); // Sauvegarder la devise associée au taux
                    log.info("✅ Taux d'achat YellowCard sauvegardé pour wallet {} : {} (currency: {}, country: {})", 
                            wallet.getId(), buyRate, currency, countryCode);
                } else {
                    log.warn("Taux d'achat YellowCard invalide ou non trouvé pour currency: {}", currency);
                }
            } else {
                log.warn("Impossible de récupérer les taux YellowCard pour sauvegarder le taux d'achat (status: {})", 
                        ratesResponse.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde du taux d'achat YellowCard pour wallet {} : {}", 
                    wallet.getId(), e.getMessage(), e);
            // Ne pas bloquer la mise à jour du wallet si la sauvegarde du taux échoue
        }
    }

    /**
     * Parse le taux YellowCard "buy" depuis la réponse JSON
     */
    private BigDecimal parseYellowCardBuyRate(String jsonResponse, String currency) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Format YellowCard: {"rates": [{"buy": 588.96, "sell": 554.98, "code": "XOF", ...}, ...]}
            // ou format direct: {"buy": 588.96, "sell": 554.98, "code": "XOF", ...}
            if (root.has("rates") && root.get("rates").isArray()) {
                for (JsonNode rateItem : root.get("rates")) {
                    if (rateItem.has("code") && rateItem.get("code").asText().equals(currency)) {
                        if (rateItem.has("buy")) {
                            return new BigDecimal(rateItem.get("buy").asText());
                        }
                        if (rateItem.has("sell")) {
                            log.warn("Taux 'buy' non trouvé pour {}, utilisation du taux 'sell' comme fallback", currency);
                            return new BigDecimal(rateItem.get("sell").asText());
                        }
                    }
                }
            }

            // Format direct (objet unique)
            if (root.has("code") && root.get("code").asText().equals(currency)) {
                if (root.has("buy")) {
                    return new BigDecimal(root.get("buy").asText());
                }
                if (root.has("sell")) {
                    log.warn("Taux 'buy' non trouvé pour {}, utilisation du taux 'sell' comme fallback", currency);
                    return new BigDecimal(root.get("sell").asText());
                }
            }

            log.warn("Impossible de trouver le taux YellowCard 'buy' pour {} dans la réponse", currency);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Erreur lors du parsing du taux YellowCard 'buy': {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Envoie la requête HTTP POST vers l'API YellowCard et renvoie un ResponseEntity contenant
     * soit le JSON parsé (ObjectNode) en cas de 201, soit le body brut ou erreur.
     */
    public ResponseEntity<Object> getObjectResponseEntity(String url, String requestBody,
                                                          String AuthorizationHeader, String timestamp) {
        HttpClient httpClient = createHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", AuthorizationHeader)
                .header("X-YC-Timestamp", timestamp)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                try {
                    JsonNode node = objectMapper.readTree(response.body());
                    return ResponseEntity.status(response.statusCode()).body(node);
                } catch (Exception e) {
                    log.warn("Réponse non JSON ou lecture échouée: {}", e.getMessage());
                    return ResponseEntity.status(response.statusCode()).body(response.body());
                }
            } else {
                log.error("❌ Erreur API YellowCard (Status: {}): Body: {}", response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).body(response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("❌ Erreur lors de l'appel à l'API YellowCard: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de l'appel à l'API YellowCard: {}"+ e.getMessage());
        }
    }

    // -----------------------
    // Utilitaires de sécurité
    // -----------------------

    /**
     * Nettoie les champs texte d'un OnRamp avant envoi à YellowCard (encodage, caractères interdits, prénom/nom).
     */
    private void sanitizeOnRampTextFields(OnRampRequest request) {
        if (request == null) {
            return;
        }
        if (request.getRecipient() != null) {
            RecipientDto recipient = request.getRecipient();
            sanitizeAndSet(recipient::getName,         recipient::setName,         YellowCardTextSanitizer::sanitizePersonName, "recipient.name");
            sanitizeAndSet(recipient::getBusinessName, recipient::setBusinessName, YellowCardTextSanitizer::sanitizeFreeText,   "recipient.businessName");
            sanitizeAndSet(recipient::getAddress,      recipient::setAddress,      YellowCardTextSanitizer::sanitizeFreeText,   "recipient.address");
        }
        if (request.getSource() != null) {
            SourceDto source = request.getSource();
            sanitizeAndSet(source::getAccountName, source::setAccountName, YellowCardTextSanitizer::sanitizePersonName, "source.accountName");
        }
        sanitizeAndSet(request::getReason, request::setReason, YellowCardTextSanitizer::sanitizeFreeText, "reason");
    }

    private void sanitizeOffRampTextFields(OffRampRequest request) {
        if (request == null) {
            return;
        }
        if (request.getSender() != null) {
            RecipientDto sender = request.getSender();
            sanitizeAndSet(sender::getName,    sender::setName,    YellowCardTextSanitizer::sanitizePersonName, "sender.name");
            sanitizeAndSet(sender::getAddress, sender::setAddress, YellowCardTextSanitizer::sanitizeFreeText,   "sender.address");
        }
        if (request.getDestination() != null) {
            SourceDto destination = request.getDestination();
            sanitizeAndSet(destination::getAccountName, destination::setAccountName, YellowCardTextSanitizer::sanitizePersonName, "destination.accountName");
            sanitizeAndSet(destination::getAccountBank, destination::setAccountBank, YellowCardTextSanitizer::sanitizeFreeText,   "destination.accountBank");
        }
        sanitizeAndSet(request::getReason, request::setReason, YellowCardTextSanitizer::sanitizeFreeText, "reason");
    }

    private void sanitizeAndSet(java.util.function.Supplier<String> getter,
                                java.util.function.Consumer<String> setter,
                                java.util.function.Function<String, String> sanitizer,
                                String fieldLabel) {
        String original = getter.get();
        if (original == null || original.isBlank()) {
            return;
        }
        String sanitized = sanitizer.apply(original);
        if (!original.equals(sanitized)) {
            log.info("YellowCard: normalisation {} : '{}' → '{}'", fieldLabel, original, sanitized);
            setter.accept(sanitized);
        }
    }

    /**
     * Remplace une valeur potentiellement nulle par une chaîne vide.
     * Conserve la même valeur si non null.
     */
    private Object safeValue(Object value) {
        return value == null ? "" : value;
    }

    /**
     * Insert la clé/valeur dans la map en remplaçant les valeurs null par "".
     */
    private void safePut(Map<String, Object> map, String key, Object value) {
        map.put(key, safeValue(value));
    }


    // -----------------------
    // Auth / signature
    // -----------------------

    private String generateSequenceId() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        return "akuunda-collection-" + timestamp;
    }

    private String generateAuthorizationHeader(String apiKey, String apiSecret,
                                               String path, String timestamp) {

        // Construire le message
        String message = timestamp + path + "GET";
        try {
            // Créer la clé secrète pour HMAC-SHA256
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            hmacSha256.init(secretKey);

            // Générer la signature
            byte[] hash = hmacSha256.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String signatureBase64 = Base64.getEncoder().encodeToString(hash);

            return "YcHmacV1 " + apiKey + ":" + signatureBase64;
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération de l'AuthorizationHeader", e);
        }
    }

    private String generateAuthorizationHeaderWithBody(String apiKey, String apiSecret, String path,
                                                       String method, String timestamp, String rawBody) {
        // Construire le message
        String message = timestamp + path + method;

        // Ajouter le hash du corps de la requête pour POST ou PUT
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            if (rawBody != null && !rawBody.isEmpty()) {
                try {
                    // Calculer le hash SHA-256 du corps brut
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] hash = digest.digest(rawBody.getBytes(StandardCharsets.UTF_8));
                    String bodyHash = Base64.getEncoder().encodeToString(hash);
                    message += bodyHash;
                } catch (Exception e) {
                    throw new RuntimeException("Erreur lors du calcul du hash du corps de la requête", e);
                }
            } else {
                // même si le corps est vide, on concatène une chaine vide (défensif)
                message += "";
            }
        }

        try {
            // Créer la clé secrète pour HMAC-SHA256
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKey);

            // Générer la signature
            byte[] hash = hmacSha256.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String signatureBase64 = Base64.getEncoder().encodeToString(hash);

            return "YcHmacV1 " + apiKey + ":" + signatureBase64;
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération de l'AuthorizationHeader", e);
        }
    }
}
