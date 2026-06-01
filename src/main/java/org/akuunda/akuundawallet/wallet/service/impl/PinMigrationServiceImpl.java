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
import org.akuunda.akuundawallet.wallet.service.PinMigrationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaSigningMethodClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Implémentation du service de migration des pincodes et emergency codes.
 * 
 * Cette migration :
 * 1. Pour les utilisateurs SANS PIN (dans Venly ou BD) : crée le PIN "111111" avec le nouveau mécanisme de hash
 * 2. Pour les utilisateurs AVEC PIN : ne change rien
 * 3. Pour les utilisateurs SANS emergency code : crée un emergency code avec les 5 derniers caractères = "ABC12"
 * 4. Pour les utilisateurs AVEC emergency code : ne change rien
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PinMigrationServiceImpl implements PinMigrationService {

    private static final String DEFAULT_PINCODE = "111111";
    private static final String DEFAULT_EMERGENCY_CODE_SUFFIX = "ABC12";

    private final UserRepository userRepository;
    private final UserPinRepository userPinRepository;
    private final UserEmergencyCodeRepository userEmergencyCodeRepository;
    private final AkunndaSigningMethodClientService signingMethodClientService;

    @Override
    public ResponseEntity<String> migrateAllUsersPincode() {
        log.info("🚀 Démarrage de la migration des pincodes pour tous les utilisateurs");
        
        try {
            // 1. Liste manuelle des userIds qui ont un PIN (basée sur les données fournies)
            // D'après les données : 23 pincodes pour 20 users (certains users ont plusieurs PINs)
            Set<String> userIdsWithPin = Set.of(
                    "39e4fc1e-07b7-4b68-a54d-9ce1998c6918",
                    "93dc1f53-519c-4d3c-9fc2-412c4a40636a",
                    "4115d888-90eb-418b-a4f4-52afb87afd78",
                    "bc11749b-1dc5-4731-882b-9928fbf955f9",
                    "01f8a941-2c88-46a3-a27d-1162911985b7",
                    "5019cf12-18fe-4934-9c07-86854d7b2be6",
                    "a7a4886a-c105-42a8-b66e-4c8f85d1d248",
                    "16c111a9-950e-49d4-8b8c-03fbf1ad8867",
                    "86a9f9a8-43ff-4f51-b34d-d664476cf35f",
                    "117d3f49-e67d-41c4-b221-cfda0568cf9d",
                    "9e2a1e18-4db5-4674-9ef1-ea9a02be182e",
                    "9cddf839-068c-4d45-90a8-cc89141990ac",
                    "66a45ad5-28c4-410f-b08e-16271249e903",
                    "22fa6541-60f3-4144-8b27-99e5398b5eea",
                    "78ef422d-d705-4001-afc9-c5f4a4e6db9e",
                    "43c418fa-fb5d-46ea-812d-bea1f5184391",
                    "4c4b91c8-11a6-4373-a1ee-d4b34ae5b4e0",
                    "1411c2db-12f2-4125-b12c-35c5362fbe06",
                    "11b582e2-15aa-4442-a6d6-cb33ddfb896c",
                    "ee86a5f9-adf3-4c95-80bb-0c3b809f33b5"
            );
            log.info("📊 Nombre de userIds avec PIN (liste manuelle): {}", userIdsWithPin.size());
            
            // 2. Liste manuelle des userIds qui ont un emergency code (basée sur les données fournies)
            // D'après les données : 8 emergency codes pour 7 users (1 user a 2 emergency codes)
            Set<String> userIdsWithEmergencyCode = Set.of(
                    "39e4fc1e-07b7-4b68-a54d-9ce1998c6918",
                    "78ef422d-d705-4001-afc9-c5f4a4e6db9e",
                    "ee86a5f9-adf3-4c95-80bb-0c3b809f33b5",
                    "9e2a1e18-4db5-4674-9ef1-ea9a02be182e",
                    "5019cf12-18fe-4934-9c07-86854d7b2be6",
                    "bc11749b-1dc5-4731-882b-9928fbf955f9"
            );
            log.info("📊 Nombre de userIds avec emergency code (liste manuelle): {}", userIdsWithEmergencyCode.size());
            
            // 3. Liste manuelle des 20 userIds à traiter
            List<String> userIdsToProcess = List.of(
                    "39e4fc1e-07b7-4b68-a54d-9ce1998c6918",
                    "93dc1f53-519c-4d3c-9fc2-412c4a40636a",
                    "4115d888-90eb-418b-a4f4-52afb87afd78",
                    "bc11749b-1dc5-4731-882b-9928fbf955f9",
                    "01f8a941-2c88-46a3-a27d-1162911985b7",
                    "5019cf12-18fe-4934-9c07-86854d7b2be6",
                    "a7a4886a-c105-42a8-b66e-4c8f85d1d248",
                    "16c111a9-950e-49d4-8b8c-03fbf1ad8867",
                    "86a9f9a8-43ff-4f51-b34d-d664476cf35f",
                    "117d3f49-e67d-41c4-b221-cfda0568cf9d",
                    "9e2a1e18-4db5-4674-9ef1-ea9a02be182e",
                    "9cddf839-068c-4d45-90a8-cc89141990ac",
                    "66a45ad5-28c4-410f-b08e-16271249e903",
                    "22fa6541-60f3-4144-8b27-99e5398b5eea",
                    "78ef422d-d705-4001-afc9-c5f4a4e6db9e",
                    "43c418fa-fb5d-46ea-812d-bea1f5184391",
                    "4c4b91c8-11a6-4373-a1ee-d4b34ae5b4e0",
                    "1411c2db-12f2-4125-b12c-35c5362fbe06",
                    "11b582e2-15aa-4442-a6d6-cb33ddfb896c",
                    "ee86a5f9-adf3-4c95-80bb-0c3b809f33b5"
            );
            
            log.info("📊 Nombre total d'utilisateurs à traiter: {}", userIdsToProcess.size());

            int pinCreatedCount = 0;
            int pinSkippedCount = 0;
            int emergencyCodeCreatedCount = 0;
            int emergencyCodeSkippedCount = 0;
            int failureCount = 0;
            int skippedCount = 0;
            StringBuilder errors = new StringBuilder();

            // 4. Parcourir uniquement les userIds de la liste manuelle
            for (String userId : userIdsToProcess) {
                if (userId == null || userId.isEmpty()) {
                    log.warn("userId null ou vide, ignoré");
                    skippedCount++;
                    continue;
                }
                
                // Récupérer l'utilisateur pour obtenir le username
                Users user = userRepository.findById(userId).orElse(null);
                String username = (user != null && user.getUsername() != null) ? user.getUsername() : "unknown";

                try {
                    log.info("🔄 Migration pour l'utilisateur: {} (userId: {})", username, userId);

                    // 3. Vérifier si l'utilisateur a déjà un PIN dans la table user_pins
                    // ⚠️ IMPORTANT : On vérifie UNIQUEMENT la BD locale, pas Venly
                    boolean hasPinInDb = userIdsWithPin.contains(userId);
                    String existingPinMethodId = null;
                    
                    if (hasPinInDb) {
                        log.info("🔍 PIN trouvé en BD pour userId: {}", userId);
                    } else {
                        log.info("🔍 Aucun PIN trouvé en BD pour userId: {}", userId);
                    }

                    // Récupérer les signing methods de Venly pour obtenir le PIN method ID si nécessaire
                    ResponseEntity<List<ExternalSigningMethod>> signingMethodsResponse = 
                            signingMethodClientService.getAllSigningMethods(userId);

                    if (signingMethodsResponse.getStatusCode() == HttpStatus.OK 
                            && signingMethodsResponse.getBody() != null) {
                        List<ExternalSigningMethod> signingMethods = signingMethodsResponse.getBody();
                        
                        if (signingMethods != null) {
                            Optional<ExternalSigningMethod> pinMethodOpt = signingMethods.stream()
                                    .filter(m -> "PIN".equals(m.getType()))
                                    .findFirst();

                            if (pinMethodOpt.isPresent()) {
                                existingPinMethodId = pinMethodOpt.get().getId();
                            }
                        }
                    }

                    // 4. Gérer le PIN
                    if (hasPinInDb) {
                        log.info("⏭️ PIN déjà existant en BD pour userId: {}, ignoré", userId);
                        pinSkippedCount++;
                    } else {
                        // Créer le PIN "111111"
                        log.info("📌 Création du PIN pour userId: {}", userId);
                        
                        // Générer le hash
                        String generated = PinHashUtil.generateRandomString(24);
                        String encrypted = PinHashUtil.hashGeneratedAndPin(generated, DEFAULT_PINCODE);

                        // Sauvegarder en BD
                        UserPin userPin = UserPin.builder()
                                .userId(userId)
                                .generatedString(generated)
                                .encryptedString(encrypted)
                                .createdAt(OffsetDateTime.now())
                                .build();
                        userPinRepository.save(userPin);
                        log.debug("✅ PIN sauvegardé en base de données pour userId: {}", userId);

                        // Créer dans Venly
                        String body = "{\"type\":\"PIN\",\"value\":\"" + DEFAULT_PINCODE + "\"}";
                        ResponseEntity<String> venlyResponse = signingMethodClientService.createUserPinSigningMethod(userId, body);

                        if (venlyResponse.getStatusCode().is2xxSuccessful()) {
                            pinCreatedCount++;
                            log.info("✅ PIN créé dans Venly pour userId: {}", userId);
                            // Mettre à jour existingPinMethodId pour l'utiliser pour l'emergency code
                            signingMethodsResponse = signingMethodClientService.getAllSigningMethods(userId);
                            if (signingMethodsResponse.getStatusCode() == HttpStatus.OK 
                                    && signingMethodsResponse.getBody() != null) {
                                List<ExternalSigningMethod> methods = signingMethodsResponse.getBody();
                                if (methods != null) {
                                    Optional<ExternalSigningMethod> newPinMethodOpt = methods.stream()
                                            .filter(m -> "PIN".equals(m.getType()))
                                            .findFirst();
                                    if (newPinMethodOpt.isPresent()) {
                                        existingPinMethodId = newPinMethodOpt.get().getId();
                                    }
                                }
                            }
                        } else {
                            // Si Venly refuse car un PIN existe déjà, essayer de mettre à jour le PIN existant
                            // en utilisant "111111" comme authentification (en supposant que c'est le PIN actuel)
                            if (existingPinMethodId != null) {
                                log.warn("⚠️ PIN existe déjà dans Venly pour userId: {}, tentative de mise à jour avec authentification", userId);
                                String pinSigningMethodHeader = existingPinMethodId + ":" + DEFAULT_PINCODE;
                                venlyResponse = signingMethodClientService.createUserOtherSigningMethod(userId, body, pinSigningMethodHeader);
                                
                                if (venlyResponse.getStatusCode().is2xxSuccessful()) {
                                    pinCreatedCount++;
                                    log.info("✅ PIN mis à jour dans Venly pour userId: {}", userId);
                                } else {
                                    failureCount++;
                                    String errorMsg = String.format("Échec création/mise à jour PIN Venly pour userId %s (username: %s): %s", 
                                            userId, username, venlyResponse.getBody());
                                    log.error("❌ {}", errorMsg);
                                    errors.append(errorMsg).append("; ");
                                    continue; // Passer au suivant si le PIN n'a pas pu être créé/mis à jour
                                }
                            } else {
                                failureCount++;
                                String errorMsg = String.format("Échec création PIN Venly pour userId %s (username: %s): %s", 
                                        userId, username, venlyResponse.getBody());
                                log.error("❌ {}", errorMsg);
                                errors.append(errorMsg).append("; ");
                                continue; // Passer au suivant si le PIN n'a pas pu être créé
                            }
                        }
                    }

                    // 5. Vérifier si l'utilisateur a déjà un emergency code dans la table user_emergency_codes
                    // ⚠️ IMPORTANT : On vérifie UNIQUEMENT la BD locale, pas Venly
                    boolean hasEmergencyCodeInDb = userIdsWithEmergencyCode.contains(userId);
                    
                    if (hasEmergencyCodeInDb) {
                        log.info("🔍 Emergency code trouvé en BD pour userId: {}", userId);
                    } else {
                        log.info("🔍 Aucun emergency code trouvé en BD pour userId: {}", userId);
                    }

                    // Récupérer les signing methods de Venly si pas déjà fait (pour obtenir le PIN method ID)
                    if (signingMethodsResponse == null || signingMethodsResponse.getStatusCode() != HttpStatus.OK) {
                        signingMethodsResponse = signingMethodClientService.getAllSigningMethods(userId);
                        // Mettre à jour existingPinMethodId si nécessaire
                        if (signingMethodsResponse.getStatusCode() == HttpStatus.OK 
                                && signingMethodsResponse.getBody() != null) {
                            List<ExternalSigningMethod> signingMethods = signingMethodsResponse.getBody();
                            if (signingMethods != null && existingPinMethodId == null) {
                                Optional<ExternalSigningMethod> pinMethodOpt = signingMethods.stream()
                                        .filter(m -> "PIN".equals(m.getType()))
                                        .findFirst();
                                if (pinMethodOpt.isPresent()) {
                                    existingPinMethodId = pinMethodOpt.get().getId();
                                }
                            }
                        }
                    }

                    // 6. Gérer l'emergency code
                    if (hasEmergencyCodeInDb) {
                        log.info("⏭️ Emergency code déjà existant en BD pour userId: {}, ignoré", userId);
                        emergencyCodeSkippedCount++;
                    } else {
                        // Créer l'emergency code avec "ABC12" comme suffixe
                        log.info("🔐 Création de l'emergency code pour userId: {}", userId);

                        // S'assurer qu'on a le PIN method ID depuis Venly
                        if (existingPinMethodId == null) {
                            // Récupérer le PIN method ID depuis Venly
                            if (signingMethodsResponse == null || signingMethodsResponse.getStatusCode() != HttpStatus.OK) {
                                signingMethodsResponse = signingMethodClientService.getAllSigningMethods(userId);
                            }
                            
                            if (signingMethodsResponse.getStatusCode() == HttpStatus.OK 
                                    && signingMethodsResponse.getBody() != null) {
                                List<ExternalSigningMethod> methods = signingMethodsResponse.getBody();
                                if (methods != null) {
                                    Optional<ExternalSigningMethod> pinMethodOpt = methods.stream()
                                            .filter(m -> "PIN".equals(m.getType()))
                                            .findFirst();
                                    if (pinMethodOpt.isPresent()) {
                                        existingPinMethodId = pinMethodOpt.get().getId();
                                    }
                                }
                            }
                        }

                        if (existingPinMethodId == null) {
                            failureCount++;
                            String errorMsg = String.format("Impossible de créer emergency code pour userId %s (username: %s): PIN method ID non trouvé dans Venly", 
                                    userId, username);
                            log.error("❌ {}", errorMsg);
                            errors.append(errorMsg).append("; ");
                            continue;
                        }

                        // ⚠️ IMPORTANT : Si l'utilisateur a un PIN dans la BD locale mais pas d'emergency code,
                        // il faut d'abord s'assurer que le PIN dans Venly est "111111"
                        // Si le PIN dans Venly n'est pas "111111", on doit le mettre à jour
                        if (hasPinInDb) {
                            log.info("🔄 Vérification/mise à jour du PIN dans Venly pour userId: {} avant création emergency code", userId);
                            // Essayer de mettre à jour le PIN dans Venly à "111111"
                            // On essaie avec "111111" comme ancien PIN (au cas où c'est déjà le bon)
                            String pinUpdateBody = "{\"type\":\"PIN\",\"value\":\"" + DEFAULT_PINCODE + "\"}";
                            String pinSigningMethodHeaderForUpdate = existingPinMethodId + ":" + DEFAULT_PINCODE;
                            ResponseEntity<String> pinUpdateResponse = 
                                    signingMethodClientService.createUserOtherSigningMethod(userId, pinUpdateBody, pinSigningMethodHeaderForUpdate);
                            
                            if (pinUpdateResponse.getStatusCode().is2xxSuccessful()) {
                                log.info("✅ PIN mis à jour dans Venly à '111111' pour userId: {}", userId);
                            } else {
                                // Si la mise à jour échoue, on continue quand même (peut-être que le PIN est déjà "111111")
                                log.warn("⚠️ Impossible de mettre à jour le PIN dans Venly pour userId: {} (réponse: {}). On continue quand même.", 
                                        userId, pinUpdateResponse.getBody());
                            }
                        }

                        // Générer 20 caractères aléatoires
                        String emergencyCodeGenerated = PinHashUtil.generateRandomString(20);
                        
                        // Chiffrer les 20 caractères
                        String encryptedGenerated = EmergencyCodeEncryptionUtil.encrypt(emergencyCodeGenerated);
                        
                        // Construire le code complet : 20 chars + "ABC12" = 25 chars
                        String fullEmergencyCode = emergencyCodeGenerated + DEFAULT_EMERGENCY_CODE_SUFFIX;
                        
                        // Créer dans Venly avec authentification PIN "111111"
                        String pinSigningMethodHeader = existingPinMethodId + ":" + DEFAULT_PINCODE;
                        String escapedEmergencyCode = fullEmergencyCode.replace("\\", "\\\\").replace("\"", "\\\"");
                        String emergencyCodeBody = "{\"type\":\"EMERGENCY_CODE\",\"value\":\"" + escapedEmergencyCode + "\"}";
                        
                        ResponseEntity<String> emergencyCodeResponse = 
                                signingMethodClientService.createUserEmergencyCodeSigningMethod(
                                        userId, emergencyCodeBody, pinSigningMethodHeader);

                        if (emergencyCodeResponse.getStatusCode().is2xxSuccessful()) {
                            // Sauvegarder uniquement les 20 caractères chiffrés en BD
                            UserEmergencyCode userEmergencyCode = UserEmergencyCode.builder()
                                    .userId(userId)
                                    .encryptedString(encryptedGenerated)
                                    .createdAt(OffsetDateTime.now())
                                    .build();
                            userEmergencyCodeRepository.save(userEmergencyCode);
                            emergencyCodeCreatedCount++;
                            log.info("✅ Emergency code créé pour userId: {} (suffixe: {})", userId, DEFAULT_EMERGENCY_CODE_SUFFIX);
                        } else {
                            // Si l'erreur est "pincode.incorrect", cela signifie que le PIN dans Venly n'est pas "111111"
                            // Dans ce cas, on ne peut pas créer l'emergency code sans connaître le vrai PIN
                            String responseBody = emergencyCodeResponse.getBody();
                            if (responseBody == null) {
                                responseBody = "";
                            }
                            if (responseBody.contains("pincode.incorrect")) {
                                failureCount++;
                                String errorMsg = String.format("Échec création emergency code Venly pour userId %s (username: %s): Le PIN dans Venly n'est pas '111111'. Impossible de créer l'emergency code sans connaître le PIN actuel. Réponse: %s", 
                                        userId, username, responseBody);
                                log.error("❌ {}", errorMsg);
                                errors.append(errorMsg).append("; ");
                            } else {
                                failureCount++;
                                String errorMsg = String.format("Échec création emergency code Venly pour userId %s (username: %s): %s", 
                                        userId, username, responseBody);
                                log.error("❌ {}", errorMsg);
                                errors.append(errorMsg).append("; ");
                            }
                        }
                    }

                } catch (Exception e) {
                    failureCount++;
                    String errorMsg = String.format("Erreur lors de la migration pour userId %s (username: %s): %s", 
                            userId, username, e.getMessage());
                    log.error("❌ {}", errorMsg, e);
                    errors.append(errorMsg).append("; ");
                }
            }

            // 7. Résumé de la migration
            String summary = String.format(
                    "{\"success\": true, \"message\": \"Migration terminée\", " +
                    "\"totalUsers\": %d, " +
                    "\"pinCreated\": %d, \"pinSkipped\": %d, " +
                    "\"emergencyCodeCreated\": %d, \"emergencyCodeSkipped\": %d, " +
                    "\"failed\": %d, \"skipped\": %d, " +
                    "\"errors\": \"%s\"}",
                    userIdsToProcess.size(), 
                    pinCreatedCount, pinSkippedCount,
                    emergencyCodeCreatedCount, emergencyCodeSkippedCount,
                    failureCount, skippedCount,
                    errors.length() > 0 ? errors.toString() : "Aucune erreur");

            log.info("🎉 Migration terminée - Total: {}, PIN créés: {}, PIN ignorés: {}, " +
                    "Emergency codes créés: {}, Emergency codes ignorés: {}, Échecs: {}, Ignorés: {}", 
                    userIdsToProcess.size(), pinCreatedCount, pinSkippedCount, 
                    emergencyCodeCreatedCount, emergencyCodeSkippedCount, failureCount, skippedCount);

            return ResponseEntity.status(HttpStatus.OK).body(summary);

        } catch (Exception e) {
            log.error("❌ Erreur critique lors de la migration des pincodes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(String.format("{\"success\": false, \"error\": \"Erreur critique: %s\"}", 
                            e.getMessage()));
        }
    }
}

