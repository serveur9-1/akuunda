package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.utils.EmergencyCodeEncryptionUtil;
import org.akuunda.akuundawallet.common.utils.PinHashUtil;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.UserEmergencyCodeRepository;
import org.akuunda.akuundawallet.wallet.api.dao.UserPinRepository;
import org.akuunda.akuundawallet.wallet.api.entities.UserEmergencyCode;
import org.akuunda.akuundawallet.wallet.api.entities.UserPin;
import org.akuunda.akuundawallet.wallet.service.PinService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaSigningMethodClientService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PinServiceImpl implements PinService {

    private final UserPinRepository userPinRepository;
    private final UserEmergencyCodeRepository userEmergencyCodeRepository;
    private final AkunndaSigningMethodClientService signingMethodClientService;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<String> definePin(String userId, String pincode) {
        try {
            // 1. Générer les strings
            String generated = PinHashUtil.generateRandomString(24);
            String encrypted = PinHashUtil.hashGeneratedAndPin(generated, pincode);

            // 2. Créer le PIN chez Venly d'abord
            String body = "{\"type\":\"PIN\",\"value\":\"" + pincode + "\"}";
            ResponseEntity<String> venlyResponse = signingMethodClientService.createUserPinSigningMethod(userId, body);

            // 3. Si Venly réussit, sauvegarder ou mettre à jour en base
            if (venlyResponse.getStatusCode().is2xxSuccessful()) {
                try {
                    // Vérifier si un PIN existe déjà pour cet utilisateur
                    Optional<UserPin> existingPinOpt = userPinRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
                    
                    UserPin entity;
                    if (existingPinOpt.isPresent()) {
                        // Mettre à jour l'enregistrement existant
                        entity = existingPinOpt.get();
                        entity.setGeneratedString(generated);
                        entity.setEncryptedString(encrypted);
                        entity.setCreatedAt(OffsetDateTime.now()); // Mise à jour de la date
                        log.info("Updating existing PIN in database for userId: {}", userId);
                    } else {
                        // Créer un nouvel enregistrement
                        entity = UserPin.builder()
                                .userId(userId)
                                .generatedString(generated)
                                .encryptedString(encrypted)
                                .createdAt(OffsetDateTime.now())
                                .build();
                        log.info("Creating new PIN in database for userId: {}", userId);
                    }
                    
                    userPinRepository.save(entity);
                    log.info("PIN saved successfully in database for userId: {}", userId);
                } catch (Exception e) {
                    log.error("Failed to save PIN in database after Venly success. userId: {}", userId, e);
                    // On retourne quand même la réponse Venly car le PIN est créé
                }
            }

            return venlyResponse;
        } catch (Exception e) {
            log.error("Error defining PIN for userId: {}", userId, e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error defining PIN: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<String> storePinSecurelyForExistingUser(String userId, String pincode) {
        try {
            log.info("Storing PIN securely in database for existing user userId: {}", userId);
            
            // 1. Générer les strings avec la même sécurité que definePin
            String generated = PinHashUtil.generateRandomString(24);
            String encrypted = PinHashUtil.hashGeneratedAndPin(generated, pincode);

            // 2. Sauvegarder ou mettre à jour en base locale (le PIN existe déjà chez Venly)
            try {
                // Vérifier si un PIN existe déjà pour cet utilisateur
                Optional<UserPin> existingPinOpt = userPinRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
                
                UserPin entity;
                if (existingPinOpt.isPresent()) {
                    // Mettre à jour l'enregistrement existant
                    entity = existingPinOpt.get();
                    entity.setGeneratedString(generated);
                    entity.setEncryptedString(encrypted);
                    entity.setCreatedAt(OffsetDateTime.now()); // Mise à jour de la date
                    log.info("Updating existing PIN in database for userId: {}", userId);
                } else {
                    // Créer un nouvel enregistrement
                    entity = UserPin.builder()
                            .userId(userId)
                            .generatedString(generated)
                            .encryptedString(encrypted)
                            .createdAt(OffsetDateTime.now())
                            .build();
                    log.info("Creating new PIN in database for userId: {}", userId);
                }
                
                userPinRepository.save(entity);
                log.info("PIN stored securely in database for userId: {}", userId);
                return ResponseEntity.status(HttpStatus.OK)
                        .body("PIN stored securely in database");
            } catch (Exception e) {
                log.error("Failed to save PIN in database for userId: {}", userId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error storing PIN: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error storing PIN securely for userId: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error storing PIN: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<String> resetPinWithEmergencyCode(String username, String emergencyCode, String newPincode) {
        String fullEmergencyCode = null;
        String generatedNewPin = null;
        String encrypted = null;
        String pinSigningMethodId = null;
        String emergencyCodeSigningMethodId = null;
        
        try {
            log.info("Resetting PIN with emergency code for username: {}", username);
            
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
            
            String userId = user.getUserId();
            log.info("🔍 Found userId: {} for username: {}", userId, username);
            
            // L'emergencyCode fourni par l'utilisateur contient les 5 caractères qu'il a mémorisés
            // On doit reconstruire le code complet (25 caractères) avec les 20 générés stockés en base
            
            // 2. Récupérer les 20 caractères générés stockés en base (chiffrés) - FAIRE CELA RAPIDEMENT
            UserEmergencyCode userEmergencyCode;
            try {
                log.debug("🔍 Searching for emergency code in database for userId: {} (username: {})", userId, username);
                var userEmergencyCodeOpt = userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
                
                if (userEmergencyCodeOpt.isEmpty()) {
                    log.error("❌ No emergency code found for userId: {} (username: {})", userId, username);
                    log.error("🔍 Checking if user exists in UserEmergencyCode table...");
                    // Vérifier si d'autres emergency codes existent pour déboguer
                    long count = userEmergencyCodeRepository.count();
                    log.debug("🔍 Total emergency codes in database: {}", count);
                    
                    String errorJson = String.format(
                        "{\"success\": false, \"error\": \"No emergency code found\", \"message\": \"No emergency code found for user. Please create an emergency code first via /api/internal/v1/emergency-code/define or during registration.\", \"username\": \"%s\", \"userId\": \"%s\"}",
                        username, userId
                    );
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(errorJson);
                }
                userEmergencyCode = userEmergencyCodeOpt.get();
                log.info("✅ Found emergency code in database for userId: {} (username: {}), created at: {}", 
                        userId, username, userEmergencyCode.getCreatedAt());
            } catch (Exception e) {
                log.error("❌ Error retrieving emergency code from database for userId: {} (username: {})", userId, username, e);
                log.error("Exception stack trace: ", e);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Database error\", \"message\": \"Error retrieving emergency code: %s\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error", username, userId
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorJson);
            }
            
            // 3. Déchiffrer les 20 caractères générés (opération mémoire, pas de DB)
            String generated = EmergencyCodeEncryptionUtil.decrypt(userEmergencyCode.getEncryptedString());
            log.debug("Decrypted generated string: {} characters", generated.length());
            
            // 4. Reconstruire l'emergency code complet = 20 générés + 5 fournis par l'utilisateur = 25 caractères
            fullEmergencyCode = generated + emergencyCode;
            log.debug("Full emergency code reconstructed: generated({}) + userProvided({}) = {} total characters", 
                    generated.length(), emergencyCode.length(), fullEmergencyCode.length());
            
            // 5. Récupérer tous les signing methods pour trouver le PIN et l'emergency code
            // FAIRE CELA APRÈS AVOIR FERMÉ LA CONNEXION DB (les appels HTTP sont longs)
            ResponseEntity<List<ExternalSigningMethod>> signingMethodsResponse = 
                    signingMethodClientService.getAllSigningMethods(userId);
            
            if (signingMethodsResponse.getStatusCode() != HttpStatus.OK 
                    || signingMethodsResponse.getBody() == null) {
                log.error("Failed to get signing methods for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Failed to retrieve signing methods\", \"message\": \"Failed to retrieve signing methods from Venly\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(errorJson);
            }
            
            List<ExternalSigningMethod> signingMethods = signingMethodsResponse.getBody();
            if (signingMethods == null || signingMethods.isEmpty()) {
                log.error("No signing methods found for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"No signing methods found\", \"message\": \"No signing methods found for user\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorJson);
            }
            
            // 6. Trouver le signing method de type PIN
            Optional<ExternalSigningMethod> pinMethodOpt = signingMethods.stream()
                    .filter(m -> "PIN".equals(m.getType()))
                    .findFirst();
            
            if (pinMethodOpt.isEmpty()) {
                log.error("PIN signing method not found for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"PIN signing method not found\", \"message\": \"PIN signing method not found for user\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorJson);
            }
            
            ExternalSigningMethod pinMethod = pinMethodOpt.get();
            pinSigningMethodId = pinMethod.getId();
            log.debug("Found PIN signing method with ID: {} for userId: {} (username: {})", pinSigningMethodId, userId, username);
            
            // 7. Trouver le signing method de type EMERGENCY_CODE
            Optional<ExternalSigningMethod> emergencyCodeMethodOpt = signingMethods.stream()
                    .filter(m -> "EMERGENCY_CODE".equals(m.getType()))
                    .findFirst();
            
            if (emergencyCodeMethodOpt.isEmpty()) {
                log.error("Emergency code signing method not found for userId: {} (username: {})", userId, username);
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Emergency code signing method not found\", \"message\": \"Emergency code signing method not found in Venly. Please create an emergency code first.\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(errorJson);
            }
            
            ExternalSigningMethod emergencyCodeMethod = emergencyCodeMethodOpt.get();
            emergencyCodeSigningMethodId = emergencyCodeMethod.getId();
            log.debug("Found emergency code signing method with ID: {} for userId: {} (username: {})", emergencyCodeSigningMethodId, userId, username);
            
            // 8. Construire le header Signing-Method avec l'emergency code (format: emergencyCodeSigningMethodId:emergencyCodeValue)
            // Conformément à la documentation Venly : https://docs.venly.io/docs/signing-methods#emergency-code
            String emergencyCodeSigningMethodHeader = emergencyCodeSigningMethodId + ":" + fullEmergencyCode;
            log.info("Emergency code signing method header constructed: {}:{}", emergencyCodeSigningMethodId, fullEmergencyCode);
            log.debug("Full emergency code length: {} characters", fullEmergencyCode.length());
            
            // 9. Générer les nouvelles strings pour le nouveau PIN (opération mémoire, pas de DB)
            generatedNewPin = PinHashUtil.generateRandomString(24);
            encrypted = PinHashUtil.hashGeneratedAndPin(generatedNewPin, newPincode);
            
            // 10. Mettre à jour le PIN chez Venly avec authentification via emergency code
            // Body: {"value": "newPincode"} (selon la doc Venly, on ne met pas "type" dans le body pour update)
            // CET APPEL HTTP PEUT PRENDRE DU TEMPS - les connexions DB sont déjà fermées
            String body = "{\"value\":\"" + newPincode + "\"}";
            ResponseEntity<String> venlyResponse = signingMethodClientService.updatePinSigningMethod(
                    userId, pinSigningMethodId, body, emergencyCodeSigningMethodHeader);
            
            // 11. Si Venly réussit, sauvegarder ou mettre à jour le nouveau PIN en base (nouvelle connexion DB, rapide)
            if (venlyResponse.getStatusCode().is2xxSuccessful()) {
                try {
                    // Vérifier si un PIN existe déjà pour cet utilisateur
                    Optional<UserPin> existingPinOpt = userPinRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
                    
                    UserPin entity;
                    if (existingPinOpt.isPresent()) {
                        // Mettre à jour l'enregistrement existant
                        entity = existingPinOpt.get();
                        entity.setGeneratedString(generatedNewPin);
                        entity.setEncryptedString(encrypted);
                        entity.setCreatedAt(OffsetDateTime.now()); // Mise à jour de la date
                        log.info("Updating existing PIN after reset in database for userId: {} (username: {})", userId, username);
                    } else {
                        // Créer un nouvel enregistrement (cas improbable mais géré)
                        entity = UserPin.builder()
                                .userId(userId)
                                .generatedString(generatedNewPin)
                                .encryptedString(encrypted)
                                .createdAt(OffsetDateTime.now())
                                .build();
                        log.info("Creating new PIN after reset in database for userId: {} (username: {})", userId, username);
                    }
                    
                    userPinRepository.saveAndFlush(entity);  // flush pour forcer l'écriture immédiate
                    log.info("PIN reset successfully and saved in database for userId: {} (username: {})", userId, username);
                } catch (Exception e) {
                    log.error("Failed to save PIN in database after Venly success. userId: {} (username: {})", userId, username, e);
                    // On retourne quand même la réponse Venly car le PIN est mis à jour
                }
            } else {
                log.error("Failed to update PIN in Venly for userId: {} (username: {}). Status: {}, Body: {}", 
                        userId, username, venlyResponse.getStatusCode(), venlyResponse.getBody());
            }
            
            // Si Venly réussit, retourner une réponse JSON formatée
            if (venlyResponse.getStatusCode().is2xxSuccessful()) {
                String successJson = String.format(
                    "{\"success\": true, \"message\": \"PIN reset successfully\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.OK).body(successJson);
            } else {
                // Si Venly échoue, retourner un message d'erreur générique pour la sécurité
                // Ne pas révéler si c'est le username, l'emergency code ou le nouveau PIN qui est incorrect
                String venlyBody = venlyResponse.getBody() != null ? venlyResponse.getBody() : "{}";
                // Vérifier si l'erreur est liée à l'authentification (PIN ou emergency code incorrect)
                boolean isAuthError = (venlyResponse.getStatusCode() == HttpStatus.UNAUTHORIZED || 
                                      venlyResponse.getStatusCode() == HttpStatus.FORBIDDEN ||
                                      (venlyBody != null && (venlyBody.contains("pincode") || venlyBody.contains("emergency") || venlyBody.contains("incorrect"))));
                
                if (isAuthError) {
                    // Message d'erreur générique pour la sécurité
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
                            safeBody, venlyResponse.getStatusCode().value(), username, userId
                        );
                        return ResponseEntity.status(venlyResponse.getStatusCode()).body(errorJson);
                    }
                }
            }
        } catch (DataAccessException e) {
            log.error("Database error while resetting PIN with emergency code for username: {}", username, e);
            String errorJson = String.format(
                "{\"success\": false, \"error\": \"Database error\", \"message\": \"Database error: %s\", \"username\": \"%s\"}",
                e.getMessage().replace("\"", "\\\""), username
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorJson);
        } catch (Exception e) {
            log.error("Error resetting PIN with emergency code for username: {}", username, e);
            String errorJson = String.format(
                "{\"success\": false, \"error\": \"Internal error\", \"message\": \"Error resetting PIN: %s\", \"username\": \"%s\"}",
                e.getMessage().replace("\"", "\\\""), username
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorJson);
        }
    }

    @Override
    public ResponseEntity<String> verifyPin(String username, String pincode) {
        try {
            log.info("Verifying PIN for username: {}", username);
            
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
            
            String userId = user.getUserId();
            log.debug("Found userId: {} for username: {}", userId, username);
            
            // 2. Récupérer le PIN stocké en base
            Optional<UserPin> userPinOpt = userPinRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
            
            if (userPinOpt.isEmpty()) {
                log.warn("No PIN found for userId: {} (username: {})", userId, username);
                // Message d'erreur générique pour la sécurité (ne pas révéler si le PIN existe ou non)
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Authentication failed\", \"message\": \"username ou codepin incorrect\", \"username\": \"%s\"}",
                    username
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(errorJson);
            }
            
            UserPin userPin = userPinOpt.get();
            String generatedString = userPin.getGeneratedString();
            String encryptedString = userPin.getEncryptedString();
            
            // 3. Vérifier le PIN avec PinHashUtil
            boolean isValid = PinHashUtil.verifyPin(encryptedString, generatedString, pincode);
            
            if (isValid) {
                log.info("PIN verified successfully for userId: {} (username: {})", userId, username);
                String successJson = String.format(
                    "{\"success\": true, \"message\": \"PIN verified successfully\", \"username\": \"%s\", \"userId\": \"%s\"}",
                    username, userId
                );
                return ResponseEntity.status(HttpStatus.OK)
                        .body(successJson);
            } else {
                log.warn("Invalid PIN provided for userId: {} (username: {})", userId, username);
                // Message d'erreur générique pour la sécurité (ne pas révéler si c'est le username ou le PIN qui est incorrect)
                String errorJson = String.format(
                    "{\"success\": false, \"error\": \"Authentication failed\", \"message\": \"username ou codepin incorrect\", \"username\": \"%s\"}",
                    username
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(errorJson);
            }
        } catch (Exception e) {
            log.error("Error verifying PIN for username: {}", username, e);
            String errorJson = String.format(
                "{\"success\": false, \"error\": \"Internal error\", \"message\": \"Error verifying PIN: %s\", \"username\": \"%s\"}",
                e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "Unknown error", username
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorJson);
        }
    }
}


