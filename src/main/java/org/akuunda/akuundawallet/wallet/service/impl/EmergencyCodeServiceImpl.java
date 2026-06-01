package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.common.utils.EmergencyCodeEncryptionUtil;
import org.akuunda.akuundawallet.common.utils.PinHashUtil;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.UserEmergencyCodeRepository;
import org.akuunda.akuundawallet.wallet.api.entities.UserEmergencyCode;
import org.akuunda.akuundawallet.wallet.service.EmergencyCodeService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaSigningMethodClientService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmergencyCodeServiceImpl implements EmergencyCodeService {

    private final AkunndaSigningMethodClientService signingMethodClientService;
    private final UserEmergencyCodeRepository userEmergencyCodeRepository;
    private final UserRepository userRepository;

    @Value("${venly.url.base}")
    private String venlyServerUrlBase;

    @Value("${venly.url.auth}")
    private String venlyAuthentificationURL;

    @Value("${venly.clientId}")
    private String venlyClientId;

    @Value("${venly.clientSecret}")
    private String venlyClientSecret;

    private static final String USER_URL = "/api/users";
    private static final String SLASH = "/";
    private static final int CODE_200 = 200;

    /**
     * Mise en prod de {@code /emergency-code/change} (20/05/2026).
     * Comptes créés ou code modifié à partir de cette date suivent la règle « 5 derniers chiffres du téléphone ».
     */
    private static final OffsetDateTime PHONE_FORMAT_RULE_EFFECTIVE_AT =
            ZonedDateTime.of(2026, 5, 20, 0, 0, 0, 0, ZoneOffset.UTC).toOffsetDateTime();

    @Override
    public ResponseEntity<String> defineEmergencyCode(String username, String partialEmergencyCode, String pincode) {
        String userId = null;
        try {
            log.info("Define emergency code for username: {}", username);
            log.debug("Parameters - username: {}, partialEmergencyCode length: {}, pincode length: {}", 
                    username, partialEmergencyCode != null ? partialEmergencyCode.length() : 0, 
                    pincode != null ? pincode.length() : 0);
            
            // 1. Récupérer l'utilisateur depuis le username pour obtenir le userId
            Users user = userRepository.getUsersByUsername(username);
            if (user == null || user.getUserId() == null) {
                log.error("User not found for username: {}", username);
                // Message d'erreur générique pour la sécurité (ne pas révéler si l'utilisateur existe ou non)
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Authentication failed\", \"message\": \"username ou codepin incorrect\", \"username\": \"%s\"}",
                    username
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(errorJson);
            }
            
            userId = user.getUserId();
            log.debug("Found userId: {} for username: {}", userId, username);
            
            // 2. Vérifier que pincode n'est pas null
            if (pincode == null || pincode.isEmpty()) {
                log.error("PIN code is null or empty for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Invalid PIN\", \"message\": \"PIN code cannot be null or empty\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(errorJson);
            }
            
            // 3. Récupérer le signing method PIN pour l'authentification
            // Retry jusqu'à 3 fois avec délai pour gérer la propagation Venly
            ResponseEntity<List<ExternalSigningMethod>> signingMethodsResponse = null;
            int retryCount = 0;
            int maxRetries = 3;
            long retryDelay = 1000; // 1 seconde
            
            while (retryCount < maxRetries) {
                signingMethodsResponse = signingMethodClientService.getAllSigningMethods(userId);
                
                if (signingMethodsResponse != null
                        && signingMethodsResponse.getStatusCode() == HttpStatus.OK) {
                    List<ExternalSigningMethod> methods = signingMethodsResponse.getBody();
                    if (methods != null && !methods.isEmpty()) {
                        // Vérifier qu'on a au moins un PIN
                        boolean hasPin = methods.stream()
                                .anyMatch(m -> "PIN".equals(m.getType()));
                        if (hasPin) {
                            log.debug("PIN signing method found on attempt {}/{}", retryCount + 1, maxRetries);
                            break;
                        }
                    }
                }
                
                retryCount++;
                if (retryCount < maxRetries) {
                    log.debug("PIN signing method not yet available, retrying ({}/{}) after {}ms delay...", 
                            retryCount, maxRetries, retryDelay);
                    try {
                        Thread.sleep(retryDelay);
                        retryDelay += 500; // Augmenter le délai à chaque retry
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrupted during retry");
                        break;
                    }
                }
            }
            
            if (signingMethodsResponse == null 
                    || signingMethodsResponse.getStatusCode() != HttpStatus.OK 
                    || signingMethodsResponse.getBody() == null) {
                log.error("Failed to get signing methods for userId: {} (username: {}) after {} retries", userId, username, retryCount);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Failed to retrieve signing methods\", \"message\": \"Failed to retrieve signing methods from Venly after %d retries\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    retryCount, username, userId
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorJson);
            }
            
            List<ExternalSigningMethod> signingMethods = signingMethodsResponse.getBody();
            if (signingMethods == null || signingMethods.isEmpty()) {
                log.error("No signing methods found for userId: {} (username: {}) after {} retries", userId, username, retryCount);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"No signing methods found\", \"message\": \"No signing methods found. Please create a PIN first.\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorJson);
            }
            
            log.debug("Found {} signing method(s) for userId: {} (username: {})", signingMethods.size(), userId, username);
            
            // 4. Trouver le signing method de type PIN
            Optional<ExternalSigningMethod> pinMethodOpt = signingMethods.stream()
                    .filter(m -> "PIN".equals(m.getType()))
                    .findFirst();
            
            if (pinMethodOpt.isEmpty()) {
                log.error("PIN signing method not found for userId: {} (username: {}) after {} retries. Available types: {}", 
                        userId, username, retryCount, 
                        signingMethods.stream().map(ExternalSigningMethod::getType).toList());
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"PIN signing method not found\", \"message\": \"PIN signing method not found. Please create a PIN first.\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorJson);
            }
            
            ExternalSigningMethod pinMethod = pinMethodOpt.get();
            String pinSigningMethodId = pinMethod.getId();
            
            // Vérifier que pinSigningMethodId n'est pas null
            if (pinSigningMethodId == null || pinSigningMethodId.isEmpty()) {
                log.error("PIN signing method ID is null or empty for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Invalid PIN signing method\", \"message\": \"PIN signing method ID is invalid\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorJson);
            }
            
            log.debug("Found PIN signing method with ID: {} for userId: {} (username: {})", pinSigningMethodId, userId, username);
            
            // 5. Construire le header Signing-Method avec le PIN (format: signingMethodId:pinValue)
            String pinSigningMethodHeader = pinSigningMethodId + ":" + pincode;
            
            // Vérifier que le header est bien construit
            if (pinSigningMethodHeader == null || pinSigningMethodHeader.isEmpty()) {
                log.error("PIN signing method header is null or empty for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Internal error\", \"message\": \"Failed to construct PIN signing method header\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorJson);
            }
            
            log.debug("PIN signing method header constructed: {} (length: {})", pinSigningMethodHeader, pinSigningMethodHeader.length());
            
            // 6. Générer une string de 20 caractères
            String generated = PinHashUtil.generateRandomString(20);
            log.debug("Generated string (20 characters): {} characters", generated.length());
            
            // 7. Chiffrer les 20 caractères générés pour les stocker en base (mesure de sécurité)
            String encryptedGenerated = EmergencyCodeEncryptionUtil.encrypt(generated);
            log.debug("Encrypted generated string created");
            
            // 8. Construire l'emergency code complet = generated (20 caractères) + partial emergencycode du client (5 caractères) = 25 caractères
            String partialCode = partialEmergencyCode != null ? partialEmergencyCode : "";
            
            // Vérifier que generated n'est pas null ou vide
            if (generated == null || generated.isEmpty()) {
                log.error("Generated emergency code is null or empty for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Internal error\", \"message\": \"Failed to generate emergency code\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorJson);
            }
            
            String fullEmergencyCode = generated + partialCode;
            log.debug("Full emergency code constructed: generated({}) + partial({}) = {} total characters", 
                    generated.length(), partialCode.length(), fullEmergencyCode.length());
            
            // Vérifier que fullEmergencyCode est valide (25 caractères)
            if (fullEmergencyCode == null || fullEmergencyCode.length() != 25) {
                log.error("Invalid full emergency code length: {} (expected 25) for userId: {} (username: {})", 
                        fullEmergencyCode != null ? fullEmergencyCode.length() : 0, userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Invalid emergency code\", \"message\": \"Emergency code must be 25 characters\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(errorJson);
            }
            
            // 9. Créer l'emergency code chez Venly (25 caractères) avec authentification PIN
            // Échapper les caractères spéciaux dans fullEmergencyCode pour le JSON
            String escapedEmergencyCode = fullEmergencyCode.replace("\\", "\\\\").replace("\"", "\\\"");
            String body = "{\"type\":\"EMERGENCY_CODE\",\"value\":\"" + escapedEmergencyCode + "\"}";
            
            if (body == null || body.isEmpty()) {
                log.error("Request body is null or empty for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Internal error\", \"message\": \"Failed to create request body\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorJson);
            }
            
            log.debug("Request body for Venly: {}", body);
            ResponseEntity<String> venlyResponse = signingMethodClientService.createUserEmergencyCodeSigningMethod(
                    userId, body, pinSigningMethodHeader);
            
            // 10. Si Venly réussit, sauvegarder uniquement les 20 caractères générés (chiffrés) en base
            // On ne stocke PAS les 5 caractères du client (mesure de sécurité)
            if (venlyResponse.getStatusCode().is2xxSuccessful()) {
                try {
                    // Vérifier si un emergency code existe déjà pour cet utilisateur
                    Optional<UserEmergencyCode> existingOpt = userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
                    
                    boolean usesPhoneLast5 = isPartialPhoneLast5(user, partialCode);
                    UserEmergencyCode entity;
                    if (existingOpt.isPresent()) {
                        // Mettre à jour l'enregistrement existant
                        entity = existingOpt.get();
                        entity.setEncryptedString(encryptedGenerated);
                        entity.setCreatedAt(OffsetDateTime.now());
                        entity.setUsesPhoneLast5(usesPhoneLast5);
                        log.info("Updating existing emergency code in database for userId: {}", userId);
                    } else {
                        // Créer un nouvel enregistrement
                        entity = UserEmergencyCode.builder()
                                .userId(userId)
                                .encryptedString(encryptedGenerated)
                                .createdAt(OffsetDateTime.now())
                                .usesPhoneLast5(usesPhoneLast5)
                                .build();
                            log.info("Creating new emergency code in database for userId: {} (username: {})", userId, username);
                    }
                    
                    userEmergencyCodeRepository.saveAndFlush(entity); // flush pour forcer l'écriture immédiate
                    log.info("✅ Generated emergency code (20 characters) saved successfully in database for userId: {} (username: {})", userId, username);
                    log.info("Partial emergency code (5 characters) NOT stored - security measure");
                    
                    // Vérifier que l'enregistrement a bien été sauvegardé
                    var verifyOpt = userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
                    if (verifyOpt.isPresent()) {
                        log.debug("✅ Verification: Emergency code confirmed in database for userId: {} (username: {})", userId, username);
                    } else {
                        log.error("❌ Verification failed: Emergency code NOT found in database after save for userId: {} (username: {})", userId, username);
                    }
                } catch (Exception e) {
                    log.error("❌ Failed to save generated emergency code in database after Venly success. userId: {} (username: {})", userId, username, e);
                    log.error("Exception details: ", e);
                    // On retourne quand même la réponse Venly car l'emergency code est créé chez Venly
                    // Mais c'est un problème critique car on ne pourra pas faire le reset PIN plus tard
                }
            } else {
                log.warn("Venly response not successful, emergency code not saved. Status: {}, Body: {}", 
                        venlyResponse.getStatusCode(), venlyResponse.getBody());
            }
            
            // Si Venly réussit, retourner une réponse JSON formatée
            if (venlyResponse.getStatusCode().is2xxSuccessful()) {
                String successJson = String.format(
                    "{\"success\": true, \"message\": \"Emergency code created successfully\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.OK).body(successJson);
            } else {
                // Si Venly échoue, vérifier si c'est une erreur d'authentification (PIN incorrect)
                String venlyBody = venlyResponse.getBody() != null ? venlyResponse.getBody() : "{}";
                // Vérifier si l'erreur est liée à l'authentification (PIN incorrect)
                boolean isAuthError = (venlyResponse.getStatusCode() == HttpStatus.UNAUTHORIZED || 
                                      venlyResponse.getStatusCode() == HttpStatus.FORBIDDEN ||
                                      (venlyBody != null && (venlyBody.contains("pincode") || venlyBody.contains("incorrect"))));
                
                if (isAuthError) {
                    // Message d'erreur générique pour la sécurité (ne pas révéler si c'est le username ou le PIN qui est incorrect)
                    String errorJson = String.format(
                        "{\"success\": false, \"error\": \"Authentication failed\", \"message\": \"username ou codepin incorrect\", \"username\": \"%s\"}",
                        username
                    );
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorJson);
                } else {
                    // Pour les autres erreurs, retourner la réponse Venly mais formatée en JSON si possible
                    if (venlyBody != null && venlyBody.trim().startsWith("{")) {
                        return venlyResponse;
                    } else {
                        // Sinon, on formate en JSON
                        String safeBody = venlyBody != null ? venlyBody.replace("\"", "\\\"") : "Unknown error";
                        String errorJson = String.format(
                            "{\"success\": false, \"error\": \"Venly error\", \"message\": \"%s\", \"status\": %d, \"username\": \"%s\", \"userId\": \"%s\"}",
                            safeBody, venlyResponse.getStatusCode().value(), username, userId != null ? userId : "unknown"
                        );
                        return ResponseEntity.status(venlyResponse.getStatusCode()).body(errorJson);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error defining emergency code for username: {}", username, e);
            String errorJson = String.format(
                "{\"success\": false, \"error\": \"Internal error\", \"message\": \"Error defining emergency code: %s\", \"username\": \"%s\"}",
                e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error", username
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorJson);
        }
    }

    @Override
    public ResponseEntity<String> changeEmergencyCode(String username, String currentPartialEmergencyCode, String newPartialEmergencyCode) {
        try {
            log.info("Changing emergency code for username: {}", username);

            // 1. Récupérer l'utilisateur
            Users user = userRepository.getUsersByUsername(username);
            if (user == null || user.getUserId() == null) {
                log.error("User not found for username: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\":false,\"error\":\"User not found\"}");
            }
            String userId = user.getUserId();

            // 2. Récupérer les 20 caractères générés en base
            var existingOpt = userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
            if (existingOpt.isEmpty()) {
                log.error("No emergency code record found for userId: {}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\":false,\"error\":\"No emergency code defined\"}");
            }
            String currentPrefix = EmergencyCodeEncryptionUtil.decrypt(existingOpt.get().getEncryptedString());
            String currentFullCode = currentPrefix + currentPartialEmergencyCode;

            // 3. Récupérer le signing method EMERGENCY_CODE depuis Venly via HttpClientCall (timeouté)
            HttpResponse<String> tokenResponse = HttpClientCall
                    .getAuthenticationToken(venlyAuthentificationURL, venlyClientId, venlyClientSecret);
            if (tokenResponse.statusCode() != CODE_200) {
                log.error("Failed to get Venly token for changeEmergencyCode. Status: {}", tokenResponse.statusCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"success\":false,\"error\":\"Failed to authenticate with Venly\"}");
            }
            JSONObject tokenJson = new JSONObject(tokenResponse.body());
            String venlyToken = tokenJson.getString("access_token");

            // GET /api/users/{userId} — Venly embeds signing methods in the user object
            String userUrl = venlyServerUrlBase + USER_URL + SLASH + userId;
            HttpResponse<String> userHttpResponse = HttpClientCall.httpGet(venlyToken, userUrl);
            if (userHttpResponse.statusCode() != CODE_200) {
                log.error("Failed to get user from Venly. Status: {}, Body: {}", userHttpResponse.statusCode(), userHttpResponse.body());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"success\":false,\"error\":\"Failed to retrieve user from Venly\"}");
            }

            // Parse signing methods from user JSON: { "result": { "signingMethods": [...] } }
            org.json.JSONObject userBody = new org.json.JSONObject(userHttpResponse.body());
            org.json.JSONObject result = userBody.optJSONObject("result");
            org.json.JSONArray smArray = result != null ? result.optJSONArray("signingMethods") : null;
            String emergencySigningMethodId = null;
            if (smArray != null) {
                for (int i = 0; i < smArray.length(); i++) {
                    org.json.JSONObject sm = smArray.optJSONObject(i);
                    if (sm != null && "EMERGENCY_CODE".equals(sm.optString("type"))) {
                        emergencySigningMethodId = sm.optString("id");
                        break;
                    }
                }
            }
            if (emergencySigningMethodId == null || emergencySigningMethodId.isBlank()) {
                log.error("Emergency code signing method not found in Venly response for userId: {}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\":false,\"error\":\"Emergency code signing method not found\"}");
            }

            // 4. Générer le nouveau prefix (20 chars) + construire le nouveau code complet
            String newPrefix = PinHashUtil.generateRandomString(20);
            String newEncryptedPrefix = EmergencyCodeEncryptionUtil.encrypt(newPrefix);
            String newFullCode = newPrefix + newPartialEmergencyCode;

            if (newFullCode.length() != 25) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\":false,\"error\":\"New emergency code must be 25 characters\"}");
            }

            // 5. Appeler Venly PUT avec HttpClientCall (timeouté) — token déjà obtenu à l'étape 3
            String signingHeader = emergencySigningMethodId + ":" + currentFullCode;
            String escapedNew = newFullCode.replace("\\", "\\\\").replace("\"", "\\\"");
            String putBody = "{\"value\":\"" + escapedNew + "\"}";
            String putUrl = venlyServerUrlBase + USER_URL + SLASH + userId + "/signing-methods/" + emergencySigningMethodId;

            log.info("Calling Venly PUT to update emergency code for userId: {}", userId);
            HttpResponse<String> putResponse = HttpClientCall.httpPutWithSigning(venlyToken, putUrl, putBody, signingHeader);
            log.info("Venly PUT response status: {}", putResponse.statusCode());

            if (putResponse.statusCode() != CODE_200) {
                log.error("Venly rejected emergency code update. Status: {}, Body: {}", putResponse.statusCode(), putResponse.body());
                boolean isAuthError = putResponse.statusCode() == 401 || putResponse.statusCode() == 403;
                if (isAuthError) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body("{\"success\":false,\"error\":\"Current emergency code is incorrect\"}");
                }
                return ResponseEntity.status(HttpStatus.valueOf(putResponse.statusCode()))
                        .body("{\"success\":false,\"error\":\"Venly error\",\"status\":" + putResponse.statusCode() + "}");
            }

            // 6. Mettre à jour la base avec le nouveau prefix chiffré
            UserEmergencyCode entity = existingOpt.get();
            entity.setEncryptedString(newEncryptedPrefix);
            entity.setCreatedAt(OffsetDateTime.now());
            entity.setUsesPhoneLast5(isPartialPhoneLast5(user, newPartialEmergencyCode));
            userEmergencyCodeRepository.saveAndFlush(entity);

            log.info("✅ Emergency code updated successfully for userId: {}", userId);
            return ResponseEntity.ok("{\"success\":true,\"message\":\"Emergency code updated successfully\"}");

        } catch (Exception e) {
            log.error("Error changing emergency code for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\":false,\"error\":\"Internal error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    @Override
    public boolean verifyEmergencyCode(String userId, String emergencyCode) {
        try {
            log.info("Verifying emergency code for userId: {}", userId);
            
            // L'emergencyCode fourni par l'utilisateur contient les 5 caractères qu'il a mémorisés
            // On doit reconstruire le code complet (25 caractères) avec les 20 générés stockés en base
            
            // 1. Récupérer les 20 caractères générés stockés en base (chiffrés)
            var userEmergencyCodeOpt = userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
            if (userEmergencyCodeOpt.isEmpty()) {
                log.error("No emergency code found for userId: {}", userId);
                return false;
            }
            
            UserEmergencyCode userEmergencyCode = userEmergencyCodeOpt.get();
            
            // 2. Déchiffrer les 20 caractères générés
            String generated = EmergencyCodeEncryptionUtil.decrypt(userEmergencyCode.getEncryptedString());
            log.debug("Decrypted generated string: {} characters", generated.length());
            
            // 3. Reconstruire l'emergency code complet = 20 générés + 5 fournis par l'utilisateur = 25 caractères
            String fullEmergencyCode = generated + emergencyCode;
            log.debug("Full emergency code reconstructed: generated({}) + userProvided({}) = {} total characters", 
                    generated.length(), emergencyCode.length(), fullEmergencyCode.length());
            
            // 4. Récupérer tous les signing methods pour trouver l'emergency code
            ResponseEntity<List<ExternalSigningMethod>> signingMethodsResponse = 
                    signingMethodClientService.getAllSigningMethods(userId);
            
            if (signingMethodsResponse.getStatusCode() != HttpStatus.OK 
                    || signingMethodsResponse.getBody() == null) {
                log.error("Failed to get signing methods for userId: {}", userId);
                return false;
            }
            
            List<ExternalSigningMethod> signingMethods = signingMethodsResponse.getBody();
            if (signingMethods == null || signingMethods.isEmpty()) {
                log.error("No signing methods found for userId: {}", userId);
                return false;
            }
            
            // 5. Trouver le signing method de type EMERGENCY_CODE
            Optional<ExternalSigningMethod> emergencyCodeMethodOpt = signingMethods.stream()
                    .filter(m -> "EMERGENCY_CODE".equals(m.getType()))
                    .findFirst();
            
            if (emergencyCodeMethodOpt.isEmpty()) {
                log.error("Emergency code signing method not found for userId: {}", userId);
                return false;
            }
            
            ExternalSigningMethod emergencyCodeMethod = emergencyCodeMethodOpt.get();
            String signingMethodId = emergencyCodeMethod.getId();
            log.debug("Found emergency code signing method with ID: {} for userId: {}", signingMethodId, userId);
            
            // 6. Vérifier l'emergency code complet (25 caractères) en l'utilisant pour authentifier une requête chez Venly
            try {
                // Obtenir le token Venly
                HttpResponse<String> tokenResponse = HttpClientCall
                        .getAuthenticationToken(venlyAuthentificationURL, venlyClientId, venlyClientSecret);
                
                if (tokenResponse.statusCode() != CODE_200) {
                    log.error("Failed to get Venly token. Status: {}", tokenResponse.statusCode());
                    return false;
                }
                
                String tokenBody = tokenResponse.body();
                if (tokenBody == null || tokenBody.isEmpty()) {
                    log.error("Venly token response body is empty");
                    return false;
                }
                
                JSONObject tokenJson = new JSONObject(tokenBody);
                String apiKey = tokenJson.getString("access_token");
                
                // Utiliser l'emergency code complet (25 caractères) pour authentifier une requête
                // Format Signing-Method : id:value (où value = les 25 caractères)
                String signingMethodHeader = signingMethodId + ":" + fullEmergencyCode;
                
                // Tester avec une requête simple : récupérer les infos utilisateur avec l'emergency code complet
                HttpResponse<String> testResponse = HttpClientCall.httpGetWithBodyAndHeader(
                        apiKey,
                        venlyServerUrlBase + USER_URL + SLASH + userId,
                        "",
                        signingMethodHeader
                );
                
                // Si la requête réussit, l'emergency code est valide
                boolean isValid = testResponse.statusCode() == CODE_200;
                
                if (isValid) {
                    log.info("Emergency code verified successfully for userId: {}", userId);
                } else {
                    log.error("Emergency code verification failed for userId: {}. Status: {}", userId, testResponse.statusCode());
                }
                
                return isValid;
            } catch (IOException | InterruptedException e) {
                log.error("Exception while verifying emergency code with Venly", e);
                return false;
            } catch (org.json.JSONException e) {
                log.error("Failed to parse Venly token response", e);
                return false;
            }
        } catch (Exception e) {
            log.error("Error verifying emergency code for userId: {}", userId, e);
            return false;
        }
    }

    @Override
    public boolean verifyPartialEmergencyCode(String username, String partialEmergencyCode) {
        if (username == null || username.isBlank()
                || partialEmergencyCode == null || partialEmergencyCode.length() != 5) {
            return false;
        }
        Users user = resolveUser(username);
        if (user == null || user.getUserId() == null) {
            log.warn("verifyPartialEmergencyCode: user not found for username {}", username);
            return false;
        }
        return verifyEmergencyCode(user.getUserId(), partialEmergencyCode);
    }

    @Override
    public boolean hasEmergencyCodeConfigured(String username) {
        Users user = resolveUser(username);
        if (user == null || user.getUserId() == null) {
            log.info("hasEmergencyCodeConfigured: user not found for {}", username);
            return false;
        }
        if (userEmergencyCodeRepository
                .findTopByUserIdOrderByCreatedAtDesc(user.getUserId())
                .isPresent()) {
            log.info("hasEmergencyCodeConfigured: true (DB row) for {}", user.getUsername());
            return true;
        }
        boolean inVenly = hasEmergencyCodeInVenly(user.getUserId());
        log.info(
                "hasEmergencyCodeConfigured: {} (Venly only) for {} userId={}",
                inVenly,
                user.getUsername(),
                user.getUserId());
        return inVenly;
    }

    /**
     * Règle du 20/05/2026 : conformes sans migration si compte créé ou code modifié à partir
     * de cette date ; les comptes plus anciens avec un code antérieur doivent migrer.
     */
    @Override
    public boolean requiresPhoneFormatEmergencyCodeUpdate(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Users user = resolveUser(username);
        if (user == null || user.getUserId() == null) {
            log.warn("requiresPhoneFormatEmergencyCodeUpdate: user not found for {}", username);
            return false;
        }

        if (isUserCreatedOnOrAfterRuleEffective(user)) {
            log.info(
                    "No migration for {} (account created on/after {})",
                    user.getUsername(),
                    PHONE_FORMAT_RULE_EFFECTIVE_AT);
            return false;
        }

        var rowOpt = userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(user.getUserId());
        if (rowOpt.isPresent()) {
            UserEmergencyCode row = rowOpt.get();
            if (isEmergencyCodeUpdatedOnOrAfterRuleEffective(row)) {
                log.info(
                        "No migration for {} (emergency code updated on/after {}, uses_phone_last5={})",
                        user.getUsername(),
                        PHONE_FORMAT_RULE_EFFECTIVE_AT,
                        row.isUsesPhoneLast5());
                return false;
            }
            log.info(
                    "Migration required for {} (legacy account, code before {}, uses_phone_last5={})",
                    user.getUsername(),
                    PHONE_FORMAT_RULE_EFFECTIVE_AT,
                    row.isUsesPhoneLast5());
            return true;
        }

        if (hasEmergencyCodeInVenly(user.getUserId())) {
            log.info(
                    "Migration required for {} (Venly EMERGENCY_CODE, no DB row, legacy account)",
                    user.getUsername());
            return true;
        }
        return false;
    }

    @Override
    public boolean isUsesPhoneLast5FlagSet(String username) {
        Users user = resolveUser(username);
        if (user == null || user.getUserId() == null) {
            return false;
        }
        return userEmergencyCodeRepository
                .findTopByUserIdOrderByCreatedAtDesc(user.getUserId())
                .map(UserEmergencyCode::isUsesPhoneLast5)
                .orElse(false);
    }

    @Override
    public boolean isMigrationStatusUserFound(String username) {
        return resolveUser(username) != null;
    }

    private Users resolveUser(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        Users user = userRepository.getUsersByUsername(username);
        if (user != null) {
            return user;
        }
        return userRepository
                .findFirstByUsernameIgnoreCase(username)
                .or(() -> userRepository.findFirstByMobilePhoneOrderByCreatedAtAsc(username))
                .or(() -> userRepository.findFirstByMobilePhoneIgnoreCase(username))
                .or(() -> userRepository.findFirstByPhoneOrUsernameDigits(username))
                .orElse(null);
    }

    private boolean hasEmergencyCodeInVenly(String userId) {
        if (hasEmergencyCodeInVenlyViaClient(userId)) {
            return true;
        }
        return hasEmergencyCodeInVenlyViaDirectHttp(userId);
    }

    private boolean hasEmergencyCodeInVenlyViaClient(String userId) {
        try {
            ResponseEntity<List<ExternalSigningMethod>> response =
                    signingMethodClientService.getAllSigningMethods(userId);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn(
                        "hasEmergencyCodeInVenlyViaClient: status {} for userId {}",
                        response.getStatusCode(),
                        userId);
                return false;
            }
            return response.getBody().stream()
                    .anyMatch(m -> m != null && "EMERGENCY_CODE".equals(m.getType()));
        } catch (Exception e) {
            log.warn("hasEmergencyCodeInVenlyViaClient failed for userId {}", userId, e);
            return false;
        }
    }

    /** Même source que {@code changeEmergencyCode} (GET /api/users/{userId} Venly). */
    private boolean hasEmergencyCodeInVenlyViaDirectHttp(String userId) {
        try {
            HttpResponse<String> tokenResponse = HttpClientCall.getAuthenticationToken(
                    venlyAuthentificationURL, venlyClientId, venlyClientSecret);
            if (tokenResponse.statusCode() != CODE_200) {
                log.warn(
                        "hasEmergencyCodeInVenlyViaDirectHttp: Venly token status {} for userId {}",
                        tokenResponse.statusCode(),
                        userId);
                return false;
            }
            String venlyToken = new JSONObject(tokenResponse.body()).getString("access_token");
            String userUrl = venlyServerUrlBase + USER_URL + SLASH + userId;
            HttpResponse<String> userHttpResponse = HttpClientCall.httpGet(venlyToken, userUrl);
            if (userHttpResponse.statusCode() != CODE_200) {
                log.warn(
                        "hasEmergencyCodeInVenlyViaDirectHttp: GET user status {} for userId {}",
                        userHttpResponse.statusCode(),
                        userId);
                return false;
            }
            JSONObject result = new JSONObject(userHttpResponse.body()).optJSONObject("result");
            org.json.JSONArray smArray = result != null ? result.optJSONArray("signingMethods") : null;
            if (smArray == null) {
                return false;
            }
            for (int i = 0; i < smArray.length(); i++) {
                JSONObject sm = smArray.optJSONObject(i);
                if (sm != null && "EMERGENCY_CODE".equals(sm.optString("type"))) {
                    log.info("hasEmergencyCodeInVenlyViaDirectHttp: EMERGENCY_CODE found for userId {}", userId);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("hasEmergencyCodeInVenlyViaDirectHttp failed for userId {}", userId, e);
            return false;
        }
    }

    /** Compte créé à partir de la mise en prod du 20/05/2026. */
    private boolean isUserCreatedOnOrAfterRuleEffective(Users user) {
        if (user.getCreatedAt() == null) {
            return false;
        }
        OffsetDateTime created = user.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC);
        return !created.isBefore(PHONE_FORMAT_RULE_EFFECTIVE_AT);
    }

    /**
     * Code défini ou modifié via define/change/acknowledge depuis le 20/05/2026
     * ({@code createdAt} mis à jour à chaque opération).
     */
    private boolean isEmergencyCodeUpdatedOnOrAfterRuleEffective(UserEmergencyCode row) {
        if (row.getCreatedAt() == null) {
            return false;
        }
        return !row.getCreatedAt().isBefore(PHONE_FORMAT_RULE_EFFECTIVE_AT);
    }

    /** Vérifie chez Venly que le code enregistré = les 5 derniers chiffres du mobile. */
    private boolean isEmergencyCodeVerifiedAsPhoneLast5(Users user) {
        String last5 = last5DigitsFromPhone(user.getMobilePhone());
        if (last5.length() != 5) {
            last5 = last5DigitsFromPhone(user.getUsername());
        }
        if (last5.length() != 5 || user.getUserId() == null) {
            return false;
        }
        return verifyEmergencyCode(user.getUserId(), last5);
    }

    @Override
    public ResponseEntity<String> acknowledgePhoneFormatEmergencyCode(String username) {
        Users user = resolveUser(username);
        if (user == null || user.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"success\":false,\"error\":\"User not found\"}");
        }
        if (!isEmergencyCodeVerifiedAsPhoneLast5(user)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    "{\"success\":false,\"error\":\"Emergency code does not match phone last 5 digits\"}");
        }
        userEmergencyCodeRepository
                .findTopByUserIdOrderByCreatedAtDesc(user.getUserId())
                .ifPresent(row -> {
                    row.setUsesPhoneLast5(true);
                    row.setCreatedAt(OffsetDateTime.now());
                    userEmergencyCodeRepository.save(row);
                    log.info(
                            "acknowledgePhoneFormat: uses_phone_last5=true, created_at updated for {}",
                            user.getUsername());
                });
        return ResponseEntity.ok(
                "{\"success\":true,\"message\":\"Phone format emergency code acknowledged\"}");
    }

    /** {@code true} si les 5 caractères fournis = 5 derniers chiffres du mobile Akuunda Pay. */
    private static boolean isPartialPhoneLast5(Users user, String partial) {
        if (partial == null || partial.length() != 5 || user == null) {
            return false;
        }
        String last5 = last5DigitsFromPhone(user.getMobilePhone());
        if (last5.length() != 5) {
            last5 = last5DigitsFromPhone(user.getUsername());
        }
        return last5.length() == 5 && last5.equals(partial);
    }

    /** Derniers 5 chiffres numériques du téléphone (aligné avec l'app mobile). */
    private static String last5DigitsFromPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() >= 5) {
            return digits.substring(digits.length() - 5);
        }
        return digits;
    }
}
