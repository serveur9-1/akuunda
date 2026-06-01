package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.UserDto;
import org.akuunda.akuundawallet.keycloak.api.dto.UserResponse;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.UserService;
import org.akuunda.akuundawallet.wallet.api.dao.MtPelerinTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletSignatureRepository;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.MtPelerinTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.WalletSignature;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMtPelerinServiceClient;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaSigningMethodClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.TokenGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
@RequiredArgsConstructor
@Slf4j
public class AkuundaMtPelerinServiceClientImpl implements AkuundaMtPelerinServiceClient {

    private final UserService userService;
    private final UserRepository userRepository;

    @Value("${venly.url.base}")
    private String walletServerUrlBase;

    @Value("${mtpelerin.api.base-url}")
    private String mtPelerinApiBaseUrl;

    @Value("${mtpelerin.api.key}")
    private String mtPelerinApiKey;

    @Value("${mtpelerin.api.report-auth-header}")
    private String mtPelerinReportAuthHeader;

    @Value("${mtpelerin.widget.base-url}")
    private String mtPelerinWidgetBaseUrl;

    @Value("${mtpelerin.referrer}")
    private String mtPelerinReferrer;

    private static final String SIGNATURE_URL = "/api/signatures";
    private static final String MTPELERIN = "MtPelerin-";
    private static final int CODE_200 = 200;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final TokenGenerationService tokenGenerationService;
    private final AkunndaSigningMethodClientService signingMethodClientService;
    private final WalletSignatureRepository walletSignatureRepository;
    private final MtPelerinTransactionRepository mtPelerinTransactionRepository;
    private final OperationRepository operationRepository;

    @Override
    public ResponseEntity<String> ibanActivation(String username) {
        try {
            log.info("Génération de l'URL pour l'utilisateur: {}", username);

            // Vérifier que l'utilisateur existe
            log.debug("Vérification de l'existence de l'utilisateur: {}", username);
            var user = userService.getUser(username);

            if (!user.getStatusCode().is2xxSuccessful()) {
                log.error("❌ Réponse null du service utilisateur pour: {}", username);
                return new ResponseEntity<>("Utilisateur inexistant avec username " + username, HttpStatus.NOT_FOUND);
            }

            log.debug("Statut de la réponse utilisateur: {}", user.getStatusCode());

            if (!user.getStatusCode().equals(HttpStatus.OK)) {
                log.error("❌ Utilisateur non trouvé ou erreur lors de la récupération. Status: {}, User: {}",
                        user.getStatusCode(), username);
                return user.getStatusCode() == HttpStatus.NOT_FOUND
                        ? new ResponseEntity<>(HttpStatus.NOT_FOUND)
                        : new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Construire l'URL
            StringBuilder urlBuilder = new StringBuilder(mtPelerinWidgetBaseUrl);
            urlBuilder.append("?tab=buy");
            urlBuilder.append("&bsc=EUR");
            urlBuilder.append("&phone=").append(safeEncode(username));
            urlBuilder.append("&ctry=FR");
            urlBuilder.append("&lang=fr");
            urlBuilder.append("&_redir=iban");

            // Si le wallet a déjà été signé (via onramp/offramp), pré-valider automatiquement
            List<WalletSignature> sigs = walletSignatureRepository.findWalletSignatureByUserName(username);
            if (sigs != null && !sigs.isEmpty()) {
                WalletSignature ws = sigs.get(0);
                urlBuilder.append("&_hash=").append(safeEncode(ws.getMtpHash()));
                urlBuilder.append("&_code=").append(safeEncode(ws.getMtpCode()));
                log.info("✅ Signature existante incluse dans l'URL IBAN pour: {}", username);
            } else {
                log.info("ℹ️ Aucune signature pour {} — MT Pelerin demandera la validation du wallet", username);
            }

            String generatedUrl = urlBuilder.toString();
            log.info("URL IBAN générée pour: {}", username);

            return ResponseEntity.ok(generatedUrl);
        } catch (Exception e) {
            log.error("Erreur lors de la génération de l'URL pour l'utilisateur: {}", username, e);
            return new ResponseEntity<>("Erreur lors de la génération de l'URL pour l'utilisateur: " + username, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<WalletSignatureResponse> isSignedWallet(String username) {
        // verifier que le user existe
        log.debug("Vérification de l'existence de l'utilisateur: {}", username);
        var user = userService.getUser(username);
        if (user == null || !user.getStatusCode().equals(HttpStatus.OK)) {
            log.error("❌ Utilisateur non trouvé ou erreur lors de la récupération. Status: {}, User: {}",
                    user != null ? user.getStatusCode() : "null", username);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // recuperer la signature en base de données
        List<WalletSignature> walletSignatures  = walletSignatureRepository.findWalletSignatureByUserName(username);
        if (walletSignatures != null && !walletSignatures.isEmpty()) {
            WalletSignature walletSignature = walletSignatures.get(0);
            WalletSignatureResponse response = new WalletSignatureResponse(
                    walletSignature.getUserName(),
                    Boolean.TRUE,
                    walletSignature.getMtpHash()
            );
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            WalletSignatureResponse response = new WalletSignatureResponse(
                    username,
                    Boolean.FALSE,
                    null
            );
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    @Override
    public ResponseEntity<MtPelerinResponse> generateSignature(String userName, String signingPin) {

        log.info("generateSignature called with userName: {}", userName);
        log.debug("generateSignature called with signingPin: {}", signingPin != null ? "***" : "null");

        if (userName == null || userName.isEmpty()) {
            log.error("❌ Le nom d'utilisateur ne peut pas être nul ou vide.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (signingPin == null || signingPin.isEmpty()) {
            log.error("❌ Le signingPin ne peut pas être nul ou vide pour l'utilisateur: {}", userName);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        // verifier que le user existe en fonction de son nom d'utilisateur
        log.debug("Vérification de l'existence de l'utilisateur: {}", userName);
        var user = userService.getUser(userName);

        if (user == null) {
            log.error("❌ Réponse null du service utilisateur pour: {}", userName);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.debug("Statut de la réponse utilisateur: {}", user.getStatusCode());

        if (user.getStatusCode().equals(HttpStatus.OK)) {
            var userBody = user.getBody();
            if (userBody == null) {
                log.error("❌ Réponse utilisateur vide (body null) pour: {}", userName);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            if (userBody.data() == null) {
                log.error("❌ Données utilisateur null (data null) pour: {}", userName);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            if (userBody.data().getWallets() == null || userBody.data().getWallets().isEmpty()) {
                log.error("❌ Aucun wallet trouvé pour l'utilisateur: {}", userName);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            WalletUserDto walletUserDto = userBody.data().getWallets().get(0);
            String walletAddress = walletUserDto.getWalletAddress();
            if (walletAddress == null || walletAddress.isEmpty()) {
                log.debug("L'adresse du portefeuille ne peut pas être nulle ou vide.");
                log.info("L'adresse du portefeuille ne peut pas être nulle ou vide.");
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            // Générer la signature hexadécimale
            String hexSig;
            String mtpCode = generateUniqueCode();

            SignatureRequest signatureRequest = new SignatureRequest();
            signatureRequest.setWalletId(walletUserDto.getId());
            signatureRequest.setData(MTPELERIN + mtpCode);
            signatureRequest.setType("MESSAGE");
            signatureRequest.setSecretType("MATIC");

            ObjectNode signatureRequestWrapper = objectMapper.createObjectNode();
            signatureRequestWrapper.set("signatureRequest", objectMapper.valueToTree(signatureRequest));

            // Générer le token d'autorisation
            var tokenResponse = tokenGenerationService.generateToken();
            if (tokenResponse == null || tokenResponse.getBody() == null) {
                log.error("Impossible de générer le token d'autorisation");
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            String apiKey = tokenResponse.getBody();

            try {
                String signatureRequestJson = objectMapper.writeValueAsString(signatureRequestWrapper);
                final var httpResponse = HttpClientCall.signatureWalletPost(signatureRequestJson, apiKey,
                        walletServerUrlBase + SIGNATURE_URL, signingPin);
                if (httpResponse.statusCode() == CODE_200) {
                    // Lire le JSON en tant que JsonNode
                    JsonNode rootNode = objectMapper.readTree(httpResponse.body());

                    // Accéder à la valeur de "signature"
                    hexSig = rootNode.path("result").path("signature").asText();


                } else {
                    log.error("❌ Erreur lors de la génération de la signature pour l'utilisateur: {}. Status: {}, Body: {}",
                            userName, httpResponse.statusCode(), httpResponse.body());
                    return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
                }
            } catch (IOException | InterruptedException e) {
                log.error("❌ Exception lors de l'appel à l'API Venly pour l'utilisateur: {}", userName, e);
                return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
            }

            final var response = processSignatureResponse(hexSig, userName, walletAddress, mtpCode);
            if (response.getStatusCode().equals(HttpStatus.OK)) {
                log.info("Signature traitée avec succès pour l'utilisateur: {}", userName);
                log.debug("Signature traitée avec succès pour l'utilisateur: {}", userName);

                if (response.getBody() == null) {
                    log.error("❌ Le corps de la réponse est nul pour l'utilisateur: {}", userName);
                    return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
                }
                // Enregistrer la signature dans la base de données
                WalletSignature walletSignature = new WalletSignature();
                walletSignature.setMtpHash(response.getBody().getMtpHash());
                walletSignature.setUserName(userName);
                walletSignature.setWalletId(walletUserDto.getId());
                walletSignature.setMtpCode(mtpCode);
                walletSignatureRepository.save(walletSignature);
                return response;
            } else {
                log.error("❌ Erreur lors du traitement de la signature pour l'utilisateur: {}. Status: {}",
                        userName, response.getStatusCode());
                return new ResponseEntity<>(response.getStatusCode());
            }

        } else {
            log.error("❌ Utilisateur non trouvé ou erreur lors de la récupération. Status: {}, User: {}",
                    user.getStatusCode(), userName);
            if (user.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            } else {
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public ResponseEntity<String> procedToSignature(String userName, String otpCode) {

        log.info("procedToSignature called with userName: {}", userName);
        log.debug("procedToSignature called with userName: {}", userName);

        // verifier que le user existe
        log.debug("Vérification de l'existence de l'utilisateur: {}", userName);
        Users users = userRepository.getUsersByUsername(userName);
        if (users == null) {
            log.error("❌ Utilisateur non trouvé: {}", userName);
            return new ResponseEntity<>("Aucun utilisateur trouvé avec le username : " + userName , HttpStatus.NOT_FOUND);
        }

        // recuperer la signature en base de données
        WalletSignature walletSignature  = walletSignatureRepository.findWalletSignatureByUserNameAndMtpCode(userName, otpCode);
        if (walletSignature == null) {
            log.error("❌ Aucune signature trouvée pour l'utilisateur: {} avec le code: {}", userName, otpCode);
            return new ResponseEntity<>("Aucune signature trouvée pour l'utilisateur: " + userName + " avec le code: " + otpCode, HttpStatus.NOT_FOUND);
        }

        // appeller l'api mt pelerin pour proceder a la signature
        ResponseEntity<MtPelerinResponse> responseEntity = processSignatureResponse(
                walletSignature.getMtpHash(),
                userName,
                walletSignature.getWalletId(),
                walletSignature.getMtpCode()
        );
        if (responseEntity.getStatusCode().equals(HttpStatus.OK)) {
            log.info("Signature traitée avec succès pour l'utilisateur: {}", userName);
            log.debug("Signature traitée avec succès pour l'utilisateur: {}", userName);

            if (responseEntity.getBody() == null) {
                log.error("❌ Le corps de la réponse est nul pour l'utilisateur: {}", userName);
                return new ResponseEntity<>("Erreur lors du traitement de la signature pour l'utilisateur: " + userName, HttpStatus.EXPECTATION_FAILED);
            }

            String mtpHash = responseEntity.getBody().getMtpHash();
            walletSignature.setMtpHash(mtpHash);
            walletSignatureRepository.saveAndFlush(walletSignature);

            return new ResponseEntity<>(mtpHash, HttpStatus.OK);
        } else {
            log.error("❌ Erreur lors du traitement de la signature pour l'utilisateur: {}. Status: {}",
                    userName, responseEntity.getStatusCode());
            return new ResponseEntity<>("Erreur lors du traitement de la signature pour l'utilisateur: " + userName, HttpStatus.EXPECTATION_FAILED);
        }

    }


    private ResponseEntity<MtPelerinResponse> processSignatureResponse(String hexSig, String userName,
                                                                       String walletAdress, String mtpCode) {

        if (hexSig == null || hexSig.isEmpty()) {
            log.error("❌ La signature ne peut pas être nulle ou vide pour l'utilisateur: {}", userName);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (userName == null || userName.isEmpty()) {
            log.error("❌ Le nom d'utilisateur ne peut pas être nul ou vide.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            // Conversion pour Mt Pelerin
            String cleanHex = hexSig.startsWith("0x") ? hexSig.substring(2) : hexSig;
            byte[] buffer = hexStringToByteArray(cleanHex);
            String mtpHash = Base64.getEncoder().encodeToString(buffer);


            MtPelerinResponse mtpResponse = new MtPelerinResponse();
            mtpResponse.setMtpHash(mtpHash);
            mtpResponse.setMtpCode(mtpCode);
            mtpResponse.setWalletAddress(walletAdress);
            mtpResponse.setUserName(userName);

            return new ResponseEntity<>(mtpResponse, HttpStatus.OK);
        } catch (Exception e) {
            log.error("❌ Erreur de conversion de la signature pour l'utilisateur: {}", userName, e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private String generateUniqueCode() {
        int randomCode = 1000 + (int) (Math.random() * 9000);
        return String.valueOf(randomCode);
    }

    /**
     * Retourne la signature existante en base, ou en génère une nouvelle si
     * {@code pinCode} est fourni. Lance une exception HTTP 428 si aucune
     * signature n'existe et que le PIN est absent.
     */
    private WalletSignature resolveSignature(String username, String pinCode) {
        List<WalletSignature> existing = walletSignatureRepository.findWalletSignatureByUserName(username);
        if (existing != null && !existing.isEmpty()) {
            WalletSignature ws = existing.get(0);
            // Normaliser le hash stocké vers du base64 pur (sans URL-encoding)
            String normalized = normalizeHashToBase64(ws.getMtpHash());
            if (!normalized.equals(ws.getMtpHash())) {
                log.info("🔧 Normalisation du hash en base pour: {}", username);
                ws.setMtpHash(normalized);
                walletSignatureRepository.save(ws);
            }
            return ws;
        }
        if (pinCode == null || pinCode.isBlank()) {
            log.warn("⚠️ Aucune signature en base pour {} et pinCode absent", username);
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Wallet non encore signé. Fournir le pinCode pour signer automatiquement.");
        }
        log.info("🔑 Signature absente — signature automatique pour: {}", username);
        return doSign(username, pinCode);
    }

    /**
     * Normalise n'importe quel format de hash vers du base64 pur (sans URL-encoding).
     * Cas gérés :
     *  - hex brut Venly (0x + 130 chars)        → hex → bytes → base64
     *  - base64 double-URL-encodé (%252F...)     → decode 2× → base64 pur
     *  - base64 simple-URL-encodé (%2F...)       → decode 1× → base64 pur
     *  - base64 pur déjà (YDhn.../...=)          → inchangé
     */
    private String normalizeHashToBase64(String hash) {
        if (hash == null || hash.isBlank()) return hash;

        // Cas 1 : hex brut Venly
        String cleaned = hash.startsWith("0x") ? hash.substring(2) : hash;
        if (cleaned.length() == 130 && cleaned.matches("[0-9a-fA-F]+")) {
            byte[] buffer = hexStringToByteArray(cleaned);
            return Base64.getEncoder().encodeToString(buffer);
        }

        // Cas 2 : URL-encodé (simple ou double) — decoder jusqu'à obtenir base64 pur
        String decoded = hash;
        for (int i = 0; i < 3; i++) {
            if (!decoded.contains("%")) break;
            try {
                decoded = java.net.URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            } catch (Exception e) {
                break;
            }
        }
        return decoded;
    }

    /**
     * Signe le message MT Pelerin via Venly et persiste la signature en base.
     * Le backend récupère lui-même le signingMethodId Venly à partir du username.
     */
    private WalletSignature doSign(String username, String pinCode) {
        // 1. Récupérer le wallet de l'utilisateur
        var user = userService.getUser(username);
        if (user == null || !user.getStatusCode().equals(HttpStatus.OK)
                || user.getBody() == null || user.getBody().data() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + username);
        }
        var wallets = user.getBody().data().getWallets();
        if (wallets == null || wallets.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Aucun wallet trouvé pour: " + username);
        }
        WalletUserDto walletUserDto = wallets.get(0);
        String walletAddress = walletUserDto.getWalletAddress();
        if (walletAddress == null || walletAddress.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Adresse wallet vide pour: " + username);
        }

        // 2. Récupérer le signingMethodId Venly de type PIN via le userId en base
        Users localUser = userRepository.getUsersByUsername(username);
        if (localUser == null || localUser.getUserId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "UserId Venly introuvable pour: " + username);
        }
        String venlyUserId = localUser.getUserId();

        var signingMethodsResponse = signingMethodClientService.getAllSigningMethods(venlyUserId);
        if (signingMethodsResponse == null || signingMethodsResponse.getBody() == null
                || signingMethodsResponse.getBody().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Aucune méthode de signature Venly pour: " + username);
        }

        Optional<ExternalSigningMethod> pinMethod = signingMethodsResponse.getBody().stream()
                .filter(m -> "PIN".equals(m.getType()))
                .findFirst();
        if (pinMethod.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Méthode de signature PIN introuvable pour: " + username);
        }

        // 3. Construire le header Signing-Method : {signingMethodId}:{pinCode}
        String signingMethodHeader = pinMethod.get().getId() + ":" + pinCode;

        // 4. Signer le message MT Pelerin via Venly
        String mtpCode = generateUniqueCode();
        SignatureRequest signatureRequest = new SignatureRequest();
        signatureRequest.setWalletId(walletUserDto.getId());
        signatureRequest.setData(MTPELERIN + mtpCode);
        signatureRequest.setType("MESSAGE");
        signatureRequest.setSecretType("MATIC");

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("signatureRequest", objectMapper.valueToTree(signatureRequest));

        var tokenResponse = tokenGenerationService.generateToken();
        if (tokenResponse == null || tokenResponse.getBody() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Impossible de générer le token Venly");
        }
        String apiKey = tokenResponse.getBody();

        try {
            String json = objectMapper.writeValueAsString(wrapper);
            final var httpResponse = HttpClientCall.signatureWalletPost(
                    json, apiKey, walletServerUrlBase + SIGNATURE_URL, signingMethodHeader);
            if (httpResponse.statusCode() != CODE_200) {
                log.error("❌ Erreur Venly pour {}: {} — {}", username, httpResponse.statusCode(), httpResponse.body());
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.EXPECTATION_FAILED, "Échec signature Venly");
            }
            JsonNode root = objectMapper.readTree(httpResponse.body());
            String hexSig = root.path("result").path("signature").asText();

            var sigResponse = processSignatureResponse(hexSig, username, walletAddress, mtpCode);
            if (!sigResponse.getStatusCode().equals(HttpStatus.OK) || sigResponse.getBody() == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.EXPECTATION_FAILED, "Échec traitement signature");
            }

            WalletSignature ws = new WalletSignature();
            ws.setMtpHash(sigResponse.getBody().getMtpHash());
            ws.setMtpCode(mtpCode);
            ws.setUserName(username);
            ws.setWalletId(walletUserDto.getId());
            walletSignatureRepository.save(ws);
            log.info("✅ Signature générée et sauvegardée pour: {}", username);
            return ws;
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Exception lors de la signature Venly pour {}: {}", username, e.getMessage(), e);
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Erreur lors de la signature: " + e.getMessage());
        }
    }

    // ============================================
    // NOUVELLES MÉTHODES MT PELERIN
    // ============================================

    @Override
    public ResponseEntity<PriceQuoteResponse> getPriceQuote(PriceQuoteRequest request) {
        try {
            log.info("Récupération d'une estimation de prix MT Pelerin: {} {} -> {} {}",
                    request.getSourceAmount(), request.getSourceCurrency(),
                    request.getDestCurrency(), request.getDestNetwork());

            String url = mtPelerinApiBaseUrl + "/currency_rates/convert";

            String requestBody = objectMapper.writeValueAsString(request);
            log.debug("Request body: {}", requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Log de la réponse brute de MT Pelerin
            log.info("==========================================");
            log.info("MT PELERIN API RESPONSE");
            log.info("==========================================");
            log.info("Status Code: {}", response.statusCode());
            log.info("Response Body (RAW): {}", response.body());
            log.info("==========================================");

            if (response.statusCode() == HttpStatus.OK.value()) {
                PriceQuoteResponse quoteResponse = objectMapper.readValue(response.body(), PriceQuoteResponse.class);

                // Calculer les champs dérivés (exchangeRate, feesCurrency)
                quoteResponse.calculateDerivedFields();

                // Log de la réponse désérialisée
                log.info("Response désérialisée:");
                log.info("  - sourceCurrency: {}", quoteResponse.getSourceCurrency());
                log.info("  - destCurrency: {}", quoteResponse.getDestCurrency());
                log.info("  - sourceAmount: {}", quoteResponse.getSourceAmount());
                log.info("  - destAmount: {}", quoteResponse.getDestAmount());
                log.info("  - sourceNetwork: {}", quoteResponse.getSourceNetwork());
                log.info("  - destNetwork: {}", quoteResponse.getDestNetwork());
                log.info("  - exchangeRate: {} (calculé)", quoteResponse.getExchangeRate());
                log.info("  - fees: {} (networkFee + fixFee)", quoteResponse.getFees());
                log.info("  - feesCurrency: {} (identique à sourceCurrency)", quoteResponse.getFeesCurrency());
                log.info("==========================================");

                log.info("Estimation de prix récupérée avec succès: {} {} = {} {}",
                        request.getSourceAmount(), request.getSourceCurrency(),
                        quoteResponse.getDestAmount(), quoteResponse.getDestCurrency());
                return ResponseEntity.ok(quoteResponse);
            } else {
                log.error("Erreur lors de la récupération de l'estimation de prix. Status: {}, Body: {}",
                        response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'appel à l'API MT Pelerin pour l'estimation de prix", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<TransactionStatusResponse> getTransactionById(String transactionId, String username) {
        try {
            log.info("Récupération de la transactions MT Pelerin associé a id {}", transactionId);
            log.debug("Récupération de la transactions MT Pelerin associé a id {}", transactionId);

            Operation operation = operationRepository.findByOperationHash(transactionId);
            if (operation == null) {
                log.warn("Aucune opération trouvée pour la transaction ID: {}", transactionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Optional<MtPelerinTransaction> optionalMtPelerinTransaction = mtPelerinTransactionRepository
                    .findByMerchantOid(transactionId);
            if (optionalMtPelerinTransaction.isEmpty()) {
                log.warn("Aucune MtPelerinTransaction trouvée pour la transaction ID: {}", transactionId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // verifier que le user existe
            log.debug("Vérification de l'existence de l'utilisateur: {}", username);
            var user = userService.getUser(username);
            if (user == null || !user.getStatusCode().equals(HttpStatus.OK)) {
                log.error("❌ Utilisateur non trouvé ou erreur lors de la récupération. Status: {}, User: {}",
                        user != null ? user.getStatusCode() : "null", username);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            String url = String.format("%s/transactions/merchantReport/%s?oid=%s",
                    mtPelerinApiBaseUrl, mtPelerinApiKey, transactionId);

            log.debug("URL de récupération des transactions: {}", url);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-report-auth", mtPelerinReportAuthHeader)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.OK.value()) {
                MerchantTransactionResponse transactionResponse = objectMapper.readValue(
                        response.body(), MerchantTransactionResponse.class);
                log.info("Transaction récupérée avec succès pour id {}", transactionId);
                if (transactionResponse.getTxs() != null && !transactionResponse.getTxs().isEmpty()) {
                    MerchantTransactionResponse.MerchantTransaction firstTx = transactionResponse.getTxs().get(0);
                    TransactionStatusResponse transactionStatusResponse = new TransactionStatusResponse(
                            firstTx.getStatus(),
                            username,
                            firstTx.getMerchantOid()
                    );
                    // Mettre à jour le statut de l'opération en base de données
                    if (!operation.getStatus().equals(firstTx.getStatus())) {
                        log.info("Mise à jour du statut de l'opération {}: {} -> {}",
                                operation.getOperationHash(),
                                operation.getStatus(),
                                firstTx.getStatus());
                        operation.setStatus(firstTx.getStatus());
                        operation.setUpdatedAt(LocalDateTime.now());
                        operationRepository.saveAndFlush(operation);
                    }

                    // recuperer la transaction stockée en bd et modifier le status s'il est different
                    MtPelerinTransaction mtPelerinTransaction = optionalMtPelerinTransaction.get();
                    if (!mtPelerinTransaction.getStatus().equals(firstTx.getStatus())) {
                        log.info("Mise à jour du statut de la transaction MtPelerinTransaction {}: {} -> {}",
                                mtPelerinTransaction.getMerchantOid(),
                                mtPelerinTransaction.getStatus(),
                                firstTx.getStatus());
                        mtPelerinTransaction.setStatus(firstTx.getStatus());
                        mtPelerinTransactionRepository.saveAndFlush(mtPelerinTransaction);
                    }

                    return new ResponseEntity<>(transactionStatusResponse, HttpStatus.OK);
                } else {
                    log.debug("Aucune transaction trouvée pour l'ID: {}, du coup on retourne le status en base", transactionId);
                    log.info("Aucune transaction trouvée pour l'ID: {}, du coup on retourne le status en base", transactionId);
                    TransactionStatusResponse transactionStatusResponse = new TransactionStatusResponse(
                            operation.getStatus(),
                            username,
                            operation.getOperationHash()
                    );
                    return new ResponseEntity<>(transactionStatusResponse, HttpStatus.OK);
                }
            } else {
                log.error("Erreur lors de la récupération des transactions. Status: {}, Body: {}",
                        response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'appel à l'API MT Pelerin pour récupérer les transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<MerchantTransactionResponse> getMerchantTransactions(
            LocalDateTime fromDate, LocalDateTime toDate, Integer skip, Integer limit, String username) {
        try {
            log.info("Récupération des transactions MT Pelerin du {} au {} pour utilisateur: {}", fromDate, toDate, username);

            // Vérifier que le username est fourni
            if (username == null || username.isBlank()) {
                log.error("Username manquant pour la récupération des transactions MT Pelerin");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String fromDateStr = fromDate.format(formatter);
            String toDateStr = toDate.format(formatter);
            int skipValue = skip != null ? skip : 0;
            int limitValue = limit != null ? limit : 100;

            String url = String.format("%s/transactions/merchantReport/%s?fromDate=%s&toDate=%s&skip=%d&limit=%d",
                    mtPelerinApiBaseUrl, mtPelerinApiKey, fromDateStr, toDateStr, skipValue, limitValue);

            log.debug("URL de récupération des transactions: {}", url);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-report-auth", mtPelerinReportAuthHeader)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Log de la réponse brute de MT Pelerin
            log.info("==========================================");
            log.info("MT PELERIN MERCHANT REPORT API RESPONSE");
            log.info("==========================================");
            log.info("Status Code: {}", response.statusCode());
            log.info("Response Body (RAW): {}", response.body());
            log.info("==========================================");

            if (response.statusCode() == HttpStatus.OK.value()) {
                MerchantTransactionResponse transactionResponse = objectMapper.readValue(
                        response.body(), MerchantTransactionResponse.class);

                // Log de la réponse désérialisée
                log.info("Response désérialisée:");
                log.info("  - fromDate: {}", transactionResponse.getFromDate());
                log.info("  - toDate: {}", transactionResponse.getToDate());
                log.info("  - nombre de transactions: {}",
                        transactionResponse.getTxs() != null ? transactionResponse.getTxs().size() : 0);
                if (transactionResponse.getTxs() != null && !transactionResponse.getTxs().isEmpty()) {
                    log.info("  - Première transaction:");
                    MerchantTransactionResponse.MerchantTransaction firstTx = transactionResponse.getTxs().get(0);
                    log.info("    * status: {}", firstTx.getStatus());
                    log.info("    * paid: {}", firstTx.getPaid());
                    log.info("    * received: {}", firstTx.getReceived());
                    log.info("    * merchant_oid: {}", firstTx.getMerchantOid());
                    log.info("    * transaction_group_id: {}", firstTx.getTransactionGroupId());
                    log.info("    * reference: {}", firstTx.getReference());
                    log.info("    * creationDate: {}", firstTx.getCreationDate());
                    log.info("    * lastUpdate: {}", firstTx.getLastUpdate());
                }
                log.info("==========================================");

                // Filtrer les transactions pour ne garder que celles de l'utilisateur
                // Le merchant_oid a le format: {username}-{uuid}   
                String usernamePrefix = username + "-";
                List<MerchantTransactionResponse.MerchantTransaction> filteredTxs = null;
                if (transactionResponse.getTxs() != null) {
                    filteredTxs = transactionResponse.getTxs().stream()
                            .filter(tx -> {
                                String merchantOid = tx.getMerchantOid();
                                if (merchantOid == null || merchantOid.isEmpty()) {
                                    log.warn("Transaction avec merchant_oid null ou vide ignorée");
                                    return false;
                                }
                                // Vérifier que le merchant_oid commence par {username}-
                                boolean belongsToUser = merchantOid.startsWith(usernamePrefix);
                                if (!belongsToUser) {
                                    log.debug("Transaction {} n'appartient pas à l'utilisateur {} (merchant_oid: {})",
                                            merchantOid, username, merchantOid);
                                }
                                return belongsToUser;
                            })
                            .collect(java.util.stream.Collectors.toList());

                    log.info("Transactions filtrées: {} sur {} appartiennent à l'utilisateur {}",
                            filteredTxs.size(), transactionResponse.getTxs().size(), username);
                }

                // Créer une nouvelle réponse avec les transactions filtrées
                MerchantTransactionResponse filteredResponse = new MerchantTransactionResponse();
                filteredResponse.setFromDate(transactionResponse.getFromDate());
                filteredResponse.setToDate(transactionResponse.getToDate());
                filteredResponse.setTxs(filteredTxs);

                // Sauvegarder les transactions filtrées en base de données
                if (filteredTxs != null) {
                    for (MerchantTransactionResponse.MerchantTransaction tx : filteredTxs) {
                        saveOrUpdateTransaction(tx);
                    }
                }

                log.info("{} transactions récupérées avec succès pour l'utilisateur {}",
                        filteredTxs != null ? filteredTxs.size() : 0, username);
                return ResponseEntity.ok(filteredResponse);
            } else {
                log.error("Erreur lors de la récupération des transactions. Status: {}, Body: {}",
                        response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'appel à l'API MT Pelerin pour récupérer les transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<List<SimpleTransactionResponse>> getSimpleMerchantTransactions(
            LocalDateTime fromDate, LocalDateTime toDate, Integer skip, Integer limit, String username) {
        try {
            // Récupérer les transactions au format complet
            ResponseEntity<MerchantTransactionResponse> fullResponse = getMerchantTransactions(
                    fromDate, toDate, skip, limit, username);

            if (fullResponse.getStatusCode() != HttpStatus.OK || fullResponse.getBody() == null) {
                return ResponseEntity.status(fullResponse.getStatusCode()).build();
            }

            MerchantTransactionResponse response = fullResponse.getBody();
            List<MerchantTransactionResponse.MerchantTransaction> txs = response.getTxs();

            if (txs == null || txs.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            // Mapper vers le format simplifié
            List<SimpleTransactionResponse> simpleTxs = txs.stream()
                    .map(this::mapToSimpleTransaction)
                    .filter(tx -> tx != null)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(simpleTxs);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des transactions simplifiées MT Pelerin", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Mappe une transaction MT Pelerin vers le format simplifié
     */
    private SimpleTransactionResponse mapToSimpleTransaction(MerchantTransactionResponse.MerchantTransaction tx) {
        try {
            // Utiliser l'ID réel : merchant_oid (ou reference en fallback)
            String id = tx.getMerchantOid() != null && !tx.getMerchantOid().isEmpty()
                    ? tx.getMerchantOid()
                    : (tx.getReference() != null && !tx.getReference().isEmpty()
                    ? tx.getReference()
                    : null);

            if (id == null) {
                log.warn("ID manquant pour la transaction MT Pelerin");
                return null;
            }

            // Mapper le statut
            String status = mapStatus(tx.getStatus());

            // Extraire la date (utiliser creationDate ou lastUpdate)
            Instant date = tx.getCreationDate() != null
                    ? tx.getCreationDate().atZone(ZoneId.systemDefault()).toInstant()
                    : (tx.getLastUpdate() != null
                    ? tx.getLastUpdate().atZone(ZoneId.systemDefault()).toInstant()
                    : Instant.now());

            // Parser le montant et la devise depuis "received" (ex: "92 EUR" ou "11.79314818 USDC")
            ParsedAmount parsed = parseAmountAndCurrency(tx.getReceived());
            if (parsed == null) {
                log.warn("Impossible de parser le montant pour la transaction: {}", tx.getMerchantOid());
                return null;
            }

            // Filtrer : ne garder que les transactions où "received" contient une devise fiat
            // Si "received" contient une crypto, c'est une transaction crypto → exclure
            if (isCryptoCurrency(parsed.currency)) {
                log.debug("Transaction exclue (crypto): {} - devise: {}", id, parsed.currency);
                return null;
            }

            // Utiliser la currency depuis l'API (EUR, USD, XOF, etc.)
            // ⚠️ NOTE : MT Pelerin ne devrait normalement jamais avoir XOF, mais on retourne
            // la currency telle qu'elle vient de l'API sans exclusion
            String currency = parsed.currency;

            // Utiliser directement la valeur avec les décimales (pas de conversion)
            // Le champ amount dans SimpleTransactionResponse est maintenant Double pour garder les décimales
            Double amount = parsed.amount;

            // Déterminer le type de transaction (ONRAMP ou OFFRAMP)
            // Si "paid" contient EUR/USD/XOF → ONRAMP (l'utilisateur paie en fiat)
            // Si "received" contient EUR/USD/XOF → OFFRAMP (l'utilisateur reçoit du fiat)
            String type = determineTransactionType(tx.getPaid(), tx.getReceived());

            return SimpleTransactionResponse.builder()
                    .id(id)
                    .status(status)
                    .date(date)
                    .amount(amount)
                    .currency(currency)
                    .operator("MTPELERIN")
                    .type(type)
                    .build();
        } catch (Exception e) {
            log.error("Erreur lors du mapping de la transaction: {}", tx.getMerchantOid(), e);
            return null;
        }
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
     * Détermine le type de transaction MT Pelerin (ONRAMP ou OFFRAMP)
     * @param paid Montant payé (ex: "1 BTC" ou "100 EUR")
     * @param received Montant reçu (ex: "100 EUR" ou "1 BTC")
     * @return "ONRAMP" si l'utilisateur paie en fiat, "OFFRAMP" s'il reçoit du fiat
     */
    private String determineTransactionType(String paid, String received) {
        // Si "paid" contient une devise fiat → ONRAMP (l'utilisateur paie en fiat pour recevoir crypto)
        if (paid != null && !paid.isEmpty()) {
            String paidUpper = paid.toUpperCase();
            if (paidUpper.contains("EUR") || paidUpper.contains("USD") || paidUpper.contains("XOF")
                    || paidUpper.contains("XAF") || paidUpper.contains("GBP") || paidUpper.contains("CHF")) {
                return "ONRAMP";
            }
        }

        // Si "received" contient une devise fiat → OFFRAMP (l'utilisateur reçoit du fiat)
        if (received != null && !received.isEmpty()) {
            String receivedUpper = received.toUpperCase();
            if (receivedUpper.contains("EUR") || receivedUpper.contains("USD") || receivedUpper.contains("XOF")
                    || receivedUpper.contains("XAF") || receivedUpper.contains("GBP") || receivedUpper.contains("CHF")) {
                return "OFFRAMP";
            }
        }

        // Par défaut, si on ne peut pas déterminer, on retourne null (sera géré par le builder)
        return "UNKNOWN";
    }

    /**
     * Génère un ID simplifié au format AKU-YYYYMMDD-XXXX
     */
    private String generateSimpleTransactionId(MerchantTransactionResponse.MerchantTransaction tx) {
        LocalDateTime date = tx.getCreationDate() != null ? tx.getCreationDate() : tx.getLastUpdate();
        if (date == null) {
            date = LocalDateTime.now();
        }

        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uniqueId = tx.getReference() != null && !tx.getReference().isEmpty()
                ? tx.getReference().substring(0, Math.min(4, tx.getReference().length()))
                : tx.getMerchantOid() != null && tx.getMerchantOid().length() > 8
                ? tx.getMerchantOid().substring(tx.getMerchantOid().length() - 4)
                : "0000";

        return String.format("AKU-%s-%s", dateStr, uniqueId.toUpperCase());
    }

    /**
     * Mappe le statut MT Pelerin vers le format simplifié
     */
    private String mapStatus(String mtPelerinStatus) {
        if (mtPelerinStatus == null) {
            return "pending";
        }
        return switch (mtPelerinStatus.toLowerCase()) {
            case "finished" -> "completed";
            case "pending" -> "pending";
            case "failed" -> "failed";
            default -> mtPelerinStatus.toLowerCase();
        };
    }

    /**
     * Parse une chaîne comme "92 EUR" ou "10000 XOF" et extrait le montant et la devise
     */
    private ParsedAmount parseAmountAndCurrency(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return null;
        }

        // Pattern pour matcher "montant devise" (ex: "92 EUR", "10000 XOF", "11.79314818 USDC")
        Pattern pattern = Pattern.compile("([\\d.]+)\\s+([A-Z]{3,4})");
        Matcher matcher = pattern.matcher(amountStr.trim());

        if (matcher.find()) {
            try {
                double amount = Double.parseDouble(matcher.group(1));
                String currency = matcher.group(2);
                return new ParsedAmount(amount, currency);
            } catch (NumberFormatException e) {
                log.warn("Impossible de parser le montant: {}", amountStr);
                return null;
            }
        }

        log.warn("Format de montant non reconnu: {}", amountStr);
        return null;
    }

    /**
     * Convertit le montant en centimes pour XOF, garde en unités pour EUR
     */
    private Long convertToAmount(double amount, String currency) {
        if ("XOF".equalsIgnoreCase(currency) || "XAF".equalsIgnoreCase(currency)) {
            // Convertir en centimes (multiplier par 100)
            return Math.round(amount * 100);
        } else {
            // Pour EUR et autres devises, garder en unités (arrondir)
            return Math.round(amount);
        }
    }

    /**
     * Classe interne pour stocker le montant et la devise parsés
     */
    private static class ParsedAmount {
        final double amount;
        final String currency;

        ParsedAmount(double amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }
    }

    /**
     * Encode une valeur de manière sécurisée (gère les null)
     */
    private String safeEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public ResponseEntity<MtPelerinOffRampResponse> buildOffRampLink(MtPelerinOffRampRequest request, String username) {
        try {
            log.info("Construction du lien OffRamp MT Pelerin pour utilisateur: {}", username);

            // Montant en fiat (sda = montant que l'utilisateur souhaite recevoir, ex: 100 EUR)
            Double sourceAmountFiat = request.getSda();
            if (sourceAmountFiat == null || sourceAmountFiat <= 0) {
                log.error("Montant fiat invalide: {}", sourceAmountFiat);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Conversion fiat → USDC via l'endpoint getPriceQuote (MT Pelerin) : on envoie toujours des USDC à MT Pelerin, jamais des euros
            PriceQuoteRequest priceQuoteRequest = new PriceQuoteRequest();
            priceQuoteRequest.setSourceCurrency(request.getSdc());
            priceQuoteRequest.setSourceAmount(sourceAmountFiat);
            priceQuoteRequest.setDestCurrency("USDC");
            priceQuoteRequest.setSourceNetwork("fiat");
            priceQuoteRequest.setDestNetwork("matic_mainnet");
            priceQuoteRequest.setIsCardPayment(false);

            ResponseEntity<PriceQuoteResponse> quoteResponse = getPriceQuote(priceQuoteRequest);
            if (quoteResponse == null || !quoteResponse.getStatusCode().is2xxSuccessful() || quoteResponse.getBody() == null) {
                log.error("Impossible d'obtenir le prix USDC pour {} {} (getPriceQuote échec). On doit convertir en USDC avant l'envoi à MT Pelerin.",
                        sourceAmountFiat, request.getSdc());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            PriceQuoteResponse quote = quoteResponse.getBody();
            if (quote == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }
            Double cryptoAmount = quote.getDestAmount();
            if (cryptoAmount == null || cryptoAmount <= 0) {
                log.error("destAmount USDC invalide retourné par getPriceQuote: {}", cryptoAmount);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
            }

            log.info("Conversion OffRamp: {} {} → {} USDC (via getPriceQuote). Envoi de {} USDC à MT Pelerin (pas d'envoi en euro).",
                    sourceAmountFiat, request.getSdc(), cryptoAmount, cryptoAmount);

            // Générer un orderId unique
            String orderId = generateOrderId(username);
            String walletAddress;

            // verifier que le user existe puis recuperer son addresse wallet
            log.debug("Vérification de l'existence de l'utilisateur: {}", username);
            var user = userService.getUser(username);

            if (user == null) {
                log.error("❌ Réponse null du service utilisateur pour: {}", username);
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            log.debug("Statut de la réponse utilisateur: {}", user.getStatusCode());

            if (user.getStatusCode().equals(HttpStatus.OK)) {
                var userBody = user.getBody();
                if (userBody == null) {
                    log.error("❌ Réponse utilisateur vide (body null) pour: {}", username);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                if (userBody.data() == null) {
                    log.error("❌ Données utilisateur null (data null) pour: {}", username);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                if (userBody.data().getWallets() == null || userBody.data().getWallets().isEmpty()) {
                    log.error("❌ Aucun wallet trouvé pour l'utilisateur: {}", username);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                WalletUserDto walletUserDto = userBody.data().getWallets().get(0);
                walletAddress = walletUserDto.getWalletAddress();
                if (walletAddress == null || walletAddress.isEmpty()) {
                    log.debug("L'adresse du portefeuille ne peut pas être nulle ou vide.");
                    log.info("L'adresse du portefeuille ne peut pas être nulle ou vide.");
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
            } else {
                log.error("❌ Utilisateur non trouvé ou erreur lors de la récupération. Status: {}, User: {}",
                        user.getStatusCode(), username);
                if (user.getStatusCode() == HttpStatus.NOT_FOUND) {
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                } else {
                    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }

            // Récupérer ou générer la signature (signe à la volée si signingPinId fourni)
            WalletSignature walletSignature = resolveSignature(username, request.getPinCode());
            String hash = walletSignature.getMtpHash();
            String code = walletSignature.getMtpCode();

            // Construire l'URL avec les paramètres
            // ⚠️ IMPORTANT : On envoie toujours des USDC à MT Pelerin (ssa = montant USDC calculé via getPriceQuote), jamais des euros
            StringBuilder urlBuilder = new StringBuilder(mtPelerinWidgetBaseUrl);
            urlBuilder.append("?tab=sell"); // Back ajoute
            urlBuilder.append("&ssc=USDC"); // Back ajoute
            urlBuilder.append("&sdc=").append(safeEncode(request.getSdc())); // Front (devise fiat souhaitée par l'utilisateur)
            urlBuilder.append("&ssa=").append(cryptoAmount); // Montant USDC (destAmount de getPriceQuote), pas le montant fiat
            urlBuilder.append("&lang=").append(safeEncode(request.getLang())); // Front
            urlBuilder.append("&snet=matic_mainnet"); // Back ajoute
            urlBuilder.append("&net=matic_mainnet"); // Back ajoute
            urlBuilder.append("&addr=").append(safeEncode(walletAddress)); // Back ajoute
            urlBuilder.append("&rfr=").append(safeEncode(mtPelerinReferrer)); // Back ajoute
            urlBuilder.append("&phone=").append(safeEncode(request.getPhone())); // Front
            urlBuilder.append("&ctry=").append(safeEncode(request.getCtry())); // Front
            urlBuilder.append("&oid=").append(safeEncode(orderId)); // Order ID
            urlBuilder.append("&_ctkn=").append(safeEncode(mtPelerinApiKey)); // API Key
            urlBuilder.append("&_code=").append(safeEncode(code));
            urlBuilder.append("&_hash=").append(safeEncode(hash));

            String redirectUrl = urlBuilder.toString();
            log.info("Lien OffRamp généré: {}", redirectUrl);

            MtPelerinOffRampResponse response = new MtPelerinOffRampResponse();
            response.setRedirectUrl(redirectUrl);
            response.setOrderId(orderId);

            // stocker en BD la transaction OffRamp avec le status INITIALED
            // Off-ramp = sortie de fonds → DEBIT côté wallet utilisateur.
            // (Legacy : ce code passait "CREDIT", ce qui faisait apparaître les
            // retraits MT Pelerin en vert sur l'historique marchand.)
            Operation operation = new Operation();
            operation.setOperationHash(orderId);  // order id est id de la transaction
            operation.setUsername(username);
            operation.setType("DEBIT");
            operation.setStatus("INITIALED");
            operation.setDesignation("OffRamp MT Pelerin");
            operation.setDevise(request.getSdc());
            operation.setCreatedAt(LocalDateTime.now());
            operation.setUpdatedAt(LocalDateTime.now());
            operation.setAmount(cryptoAmount); // Montant USDC (destAmount de getPriceQuote)
            operationRepository.save(operation);

            MtPelerinTransaction mtPelerinTransaction = new MtPelerinTransaction();
            mtPelerinTransaction.setMerchantOid(orderId);
            mtPelerinTransaction.setTransactionType("OFFRAMP");
            mtPelerinTransaction.setStatus("INITIALED");
            mtPelerinTransaction.setCreationDate(LocalDateTime.now());
            mtPelerinTransaction.setSyncedAt(LocalDateTime.now());
            mtPelerinTransaction.setUsername(username);
            mtPelerinTransaction.setWalletAddress(walletAddress);
            mtPelerinTransactionRepository.save(mtPelerinTransaction);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors de la construction du lien OffRamp", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<MtPelerinOnRampResponse> buildOnRampLink(MtPelerinOnRampRequest request, String username) {
        try {
            log.info("Construction du lien OnRamp MT Pelerin pour utilisateur: {}", username);

            var user = userService.getUser(username);

            if (user == null) {
                log.error("❌ Réponse null du service utilisateur pour: {}", username);
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Résoudre l'adresse wallet : request ou fallback depuis le user
            String walletAddress = request.getAddr();
            if ((walletAddress == null || walletAddress.isBlank())
                    && user.getBody() != null && user.getBody().data() != null
                    && user.getBody().data().getWallets() != null
                    && !user.getBody().data().getWallets().isEmpty()) {
                walletAddress = user.getBody().data().getWallets().get(0).getWalletAddress();
            }

            // Générer un orderId unique
            String orderId = generateOrderId(username);

            // Récupérer ou générer la signature (signe à la volée si pinCode fourni)
            WalletSignature walletSignature = resolveSignature(username, request.getPinCode());
            String hash = walletSignature.getMtpHash();
            String code = walletSignature.getMtpCode();

            // Construire l'URL avec les paramètres
            StringBuilder urlBuilder = new StringBuilder(mtPelerinWidgetBaseUrl);
            urlBuilder.append("?tab=buy");
            urlBuilder.append("&bsc=").append(safeEncode(request.getBsc()));
            urlBuilder.append("&bdc=USDC");
            urlBuilder.append("&bsa=").append(request.getBsa());
            urlBuilder.append("&dnet=matic_mainnet");
            urlBuilder.append("&net=matic_mainnet");
            urlBuilder.append("&rfr=").append(safeEncode(mtPelerinReferrer));
            urlBuilder.append("&phone=").append(safeEncode(request.getPhone()));
            urlBuilder.append("&ctry=").append(safeEncode(request.getCtry()));
            urlBuilder.append("&lang=").append(safeEncode(request.getLang()));
            urlBuilder.append("&addr=").append(safeEncode(walletAddress));
            urlBuilder.append("&oid=").append(safeEncode(orderId));
            urlBuilder.append("&_ctkn=").append(safeEncode(mtPelerinApiKey));
            urlBuilder.append("&code=").append(safeEncode(code));
            urlBuilder.append("&hash=").append(safeEncode(hash));

            String redirectUrl = urlBuilder.toString();
            log.info("Lien OnRamp généré pour {}: {}", username, redirectUrl);

            MtPelerinOnRampResponse response = new MtPelerinOnRampResponse();
            response.setRedirectUrl(redirectUrl);
            response.setOrderId(orderId);

            Operation operation = new Operation();
            operation.setOperationHash(orderId);
            operation.setUsername(username);
            operation.setType("DEBIT");
            operation.setStatus("INITIALED");
            operation.setDesignation("OnRamp MT Pelerin");
            operation.setDevise(request.getBsc());
            operation.setCreatedAt(LocalDateTime.now());
            operation.setUpdatedAt(LocalDateTime.now());
            operation.setAmount(request.getBsa());
            operationRepository.save(operation);

            MtPelerinTransaction mtPelerinTransaction = new MtPelerinTransaction();
            mtPelerinTransaction.setMerchantOid(orderId);
            mtPelerinTransaction.setTransactionType("MtPelerin OnRamp");
            mtPelerinTransaction.setStatus("INITIALED");
            mtPelerinTransaction.setCreationDate(LocalDateTime.now());
            mtPelerinTransaction.setSyncedAt(LocalDateTime.now());
            mtPelerinTransaction.setUsername(username);
            mtPelerinTransaction.setWalletAddress(walletAddress);
            mtPelerinTransactionRepository.save(mtPelerinTransaction);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors de la construction du lien OnRamp", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================
    // MÉTHODES PRIVÉES
    // ============================================

    private String generateOrderId(String username) {
        // Générer un UUID unique comme orderId
        // Si username est fourni, on peut l'inclure pour faciliter le tracking
        if (username != null && !username.isEmpty()) {
            return username + "-" + UUID.randomUUID().toString();
        }
        return UUID.randomUUID().toString();
    }

    private void saveOrUpdateTransaction(MerchantTransactionResponse.MerchantTransaction tx) {
        try {
            MtPelerinTransaction existingTx = mtPelerinTransactionRepository.findByMerchantOid(tx.getMerchantOid())
                    .orElse(null);

            if (existingTx == null) {
                // Créer une nouvelle transaction
                existingTx = MtPelerinTransaction.builder()
                        .merchantOid(tx.getMerchantOid())
                        .transactionGroupId(tx.getTransactionGroupId())
                        .reference(tx.getReference())
                        .status(tx.getStatus())
                        .paid(tx.getPaid())
                        .received(tx.getReceived())
                        .creationDate(tx.getCreationDate())
                        .lastUpdate(tx.getLastUpdate())
                        .syncedAt(LocalDateTime.now())
                        .build();

                // Détecter le type de transaction basé sur les montants
                if (tx.getPaid() != null && (tx.getPaid().contains("EUR") || tx.getPaid().contains("USD"))) {
                    existingTx.setTransactionType("ONRAMP");
                } else if (tx.getReceived() != null && (tx.getReceived().contains("EUR") || tx.getReceived().contains("USD"))) {
                    existingTx.setTransactionType("OFFRAMP");
                }

                mtPelerinTransactionRepository.save(existingTx);
                log.debug("Transaction sauvegardée: {}", tx.getMerchantOid());
            } else {
                // Mettre à jour la transaction existante
                existingTx.setStatus(tx.getStatus());
                existingTx.setPaid(tx.getPaid());
                existingTx.setReceived(tx.getReceived());
                existingTx.setLastUpdate(tx.getLastUpdate());
                existingTx.setSyncedAt(LocalDateTime.now());
                mtPelerinTransactionRepository.save(existingTx);
                log.debug("Transaction mise à jour: {}", tx.getMerchantOid());
            }
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de la transaction {}", tx.getMerchantOid(), e);
        }
    }

    @Override
    public ResponseEntity<MtPelerinSignWalletResponse> signWallet(String username, String pinCode) {
        try {
            WalletSignature ws = resolveSignature(username, pinCode);

            String walletAddress = "";
            var user = userService.getUser(username);
            if (user != null && user.getBody() != null && user.getBody().data() != null
                    && !user.getBody().data().getWallets().isEmpty()) {
                walletAddress = user.getBody().data().getWallets().get(0).getWalletAddress();
            }

            log.info("✅ Wallet signé/validé pour: {}", username);
            return ResponseEntity.ok(new MtPelerinSignWalletResponse(true, walletAddress, "Wallet signé avec succès"));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            log.warn("signWallet refusé pour {}: {}", username, e.getReason());
            return ResponseEntity.status(e.getStatusCode())
                    .body(new MtPelerinSignWalletResponse(false, null, e.getReason()));
        } catch (Exception e) {
            log.error("Erreur lors de la signature du wallet pour {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MtPelerinSignWalletResponse(false, null, "Erreur interne"));
        }
    }

}
