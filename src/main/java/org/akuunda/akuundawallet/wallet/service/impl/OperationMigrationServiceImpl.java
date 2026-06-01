package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.GuadarianTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dto.MigrationResponseDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.TransactionStatusResponse;
import org.akuunda.akuundawallet.wallet.api.entities.GuardarianTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.service.OperationMigrationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaGuardarianClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Implémentation du service de migration des opérations.
 * 
 * Cette migration corrige :
 * 1. Les opérations Guardarian avec des montants null
 * 2. Les statuts non normalisés
 * 3. Les dates manquantes
 * 4. Les données incorrectes
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OperationMigrationServiceImpl implements OperationMigrationService {

    private static final Set<String> GUADARIAN_DESIGNATIONS = Set.of(
            "GUADARIAN_ONRAMP", "GUADARIAN_OFFRAMP", "GUADARIAN_SWAP"
    );
    
    private static final Set<String> VALID_STATUSES = Set.of(
            "NEW", "EN ATTENTE", "VALIDEE", "ANNULEE", "REJETEE"
    );

    private final OperationRepository operationRepository;
    private final GuadarianTransactionRepository guadarianTransactionRepository;
    private final AkuundaGuardarianClientService guardarianClientService;

    @Override
    @Transactional
    public ResponseEntity<MigrationResponseDto> migrateAllOperations() {
        log.info("🚀 Démarrage de la migration des opérations");
        
        int totalOperations = 0;
        int fixedOperations = 0;
        int fixedGuardarianOperations = 0;
        int fixedDates = 0;
        int fixedStatuses = 0;
        int fixedAmounts = 0;
        int errors = 0;
        
        // Statistiques détaillées pour diagnostic
        int guardarianOperationsFound = 0;
        int guardarianTransactionsNotFound = 0;
        int guardarianTransactionsWithoutToAmount = 0;

        try {
            // Récupérer toutes les opérations
            List<Operation> allOperations = operationRepository.findAll();
            totalOperations = allOperations.size();
            
            log.info("📊 Nombre total d'opérations à traiter : {}", totalOperations);

            for (Operation operation : allOperations) {
                try {
                    boolean wasFixed = false;

                    // 1. Corriger les dates manquantes
                    if (operation.getCreatedAt() == null) {
                        operation.setCreatedAt(LocalDateTime.now());
                        fixedDates++;
                        wasFixed = true;
                        log.debug("✅ Date createdAt ajoutée pour l'opération ID: {}", operation.getId());
                    }
                    if (operation.getUpdatedAt() == null) {
                        operation.setUpdatedAt(LocalDateTime.now());
                        fixedDates++;
                        wasFixed = true;
                        log.debug("✅ Date updatedAt ajoutée pour l'opération ID: {}", operation.getId());
                    }

            // 2. Corriger les opérations Guardarian
            if (isGuardarianOperation(operation)) {
                guardarianOperationsFound++;
                log.info("🔍 Opération Guardarian détectée ID: {}, designation: {}, operationHash: {}, amount: {}, convertedAmount: {}", 
                        operation.getId(), operation.getDesignation(), operation.getOperationHash(), 
                        operation.getAmount(), operation.getConvertedAmount());
                int amountsFixed = fixGuardarianOperation(operation);
                if (amountsFixed > 0) {
                    fixedGuardarianOperations++;
                    fixedAmounts += amountsFixed;
                    wasFixed = true;
                } else if (amountsFixed == -1) {
                    guardarianTransactionsNotFound++;
                    log.warn("⚠️ GuardarianTransaction non trouvée pour l'opération ID: {} (operationHash: {})", 
                            operation.getId(), operation.getOperationHash());
                } else if (amountsFixed == -2) {
                    guardarianTransactionsWithoutToAmount++;
                    log.warn("⚠️ GuardarianTransaction sans source pour amount pour l'opération ID: {} (operationHash: {})", 
                            operation.getId(), operation.getOperationHash());
                } else {
                    log.info("ℹ️ Aucun montant corrigé pour l'opération Guardarian ID: {} (déjà complet)", 
                            operation.getId());
                }
            }

            // 3. Normaliser les statuts
            if (operation.getStatus() != null && !VALID_STATUSES.contains(operation.getStatus())) {
                String normalizedStatus = normalizeStatus(operation.getStatus());
                if (!normalizedStatus.equals(operation.getStatus())) {
                    log.info("🔄 Normalisation du statut '{}' → '{}' pour l'opération ID: {}",
                            operation.getStatus(), normalizedStatus, operation.getId());
                    operation.setStatus(normalizedStatus);
                    fixedStatuses++;
                    wasFixed = true;
                }
            }

            // 4. Sauvegarder si des corrections ont été faites
            if (wasFixed) {
                operationRepository.save(operation);
                fixedOperations++;
                log.debug("💾 Opération ID: {} sauvegardée après corrections", operation.getId());
            }

                } catch (Exception e) {
                    errors++;
                    log.error("❌ Erreur lors de la migration de l'opération ID: {}", operation.getId(), e);
                }
            }

            // Statistiques finales
            MigrationResponseDto.MigrationStatistics statistics = MigrationResponseDto.MigrationStatistics.builder()
                    .totalOperations(totalOperations)
                    .fixedOperations(fixedOperations)
                    .fixedGuardarianOperations(fixedGuardarianOperations)
                    .fixedDates(fixedDates)
                    .fixedStatuses(fixedStatuses)
                    .fixedAmounts(fixedAmounts)
                    .errors(errors)
                    .build();

            MigrationResponseDto response = MigrationResponseDto.builder()
                    .success(true)
                    .message("Migration terminée avec succès")
                    .statistics(statistics)
                    .build();

            log.info("✅ Migration terminée : {} opérations corrigées sur {}", fixedOperations, totalOperations);
            log.info("📊 Statistiques détaillées Guardarian : {} opérations trouvées, {} transactions non trouvées, {} sans toAmount", 
                    guardarianOperationsFound, guardarianTransactionsNotFound, guardarianTransactionsWithoutToAmount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Erreur critique lors de la migration", e);
            MigrationResponseDto errorResponse = MigrationResponseDto.builder()
                    .success(false)
                    .message("Erreur lors de la migration")
                    .error(e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * Vérifie si une opération est une opération Guardarian
     */
    private boolean isGuardarianOperation(Operation operation) {
        return operation.getDesignation() != null 
                && GUADARIAN_DESIGNATIONS.contains(operation.getDesignation());
    }

    /**
     * Corrige une opération Guardarian en récupérant les données depuis GuardarianTransaction
     * @return Le nombre de montants corrigés (amount + convertedAmount)
     */
    private int fixGuardarianOperation(Operation operation) {
        int amountsFixed = 0;

        try {
            // Récupérer l'operationHash qui correspond à externalTransactionId
            String operationHash = operation.getOperationHash();
            if (operationHash == null || operationHash.isEmpty()) {
                log.warn("⚠️ Opération Guardarian ID: {} sans operationHash", operation.getId());
                return 0;
            }

            // Convertir operationHash en Long pour chercher dans GuardarianTransaction
            Long externalTransactionId;
            try {
                externalTransactionId = Long.parseLong(operationHash);
            } catch (NumberFormatException e) {
                log.warn("⚠️ OperationHash '{}' de l'opération ID: {} n'est pas un nombre valide", 
                        operationHash, operation.getId());
                return 0;
            }

            // Chercher la transaction Guardarian correspondante
            Optional<GuardarianTransaction> txOpt = guadarianTransactionRepository
                    .findByExternalTransactionId(externalTransactionId);

            if (txOpt.isEmpty()) {
                log.warn("⚠️ Aucune GuardarianTransaction trouvée en base pour externalTransactionId: {} (opération ID: {}, operationHash: {})", 
                        externalTransactionId, operation.getId(), operationHash);
                
                // Essayer de récupérer depuis l'API Guardarian
                log.info("🔄 Tentative de récupération depuis l'API Guardarian pour transaction ID: {}", externalTransactionId);
                try {
                    // Petit délai pour éviter les rate limits de l'API Guardarian
                    try {
                        Thread.sleep(500); // 500ms entre chaque appel API
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread interrupted during API call delay");
                    }
                    
                    // Utiliser le username de l'opération pour vérifier la propriété
                    String username = operation.getUsername();
                    if (username == null || username.isBlank()) {
                        log.warn("⚠️ Opération ID: {} sans username, impossible de récupérer la transaction depuis l'API", 
                                operation.getId());
                        return -1;
                    }
                    
                    ResponseEntity<TransactionStatusResponse> apiResponse =
                            guardarianClientService.getTransactionById(operationHash, username);
                    
                    if (apiResponse != null && apiResponse.getStatusCode().is2xxSuccessful()) {
                        log.info("✅ Transaction récupérée depuis l'API Guardarian pour ID: {}", externalTransactionId);
                        
                        // La méthode getTransactionById a déjà sauvegardé la transaction en base
                        // Recharger depuis la base de données
                        txOpt = guadarianTransactionRepository.findByExternalTransactionId(externalTransactionId);
                        
                        if (txOpt.isEmpty()) {
                            log.warn("⚠️ Transaction récupérée depuis l'API mais non trouvée en base après sauvegarde pour ID: {}", externalTransactionId);
                            return -1;
                        }
                    } else {
                        log.warn("⚠️ Impossible de récupérer la transaction depuis l'API Guardarian pour ID: {} (status: {})", 
                                externalTransactionId, apiResponse != null ? apiResponse.getStatusCode() : "null");
                        
                        // Fallback : calcul approximatif si on a convertedAmount
                        if (operation.getAmount() == null && operation.getConvertedAmount() != null && operation.getConvertedAmount() > 0) {
                            double approximateRate = 1.1; // 1 EUR ≈ 1.1 USDC (taux moyen approximatif)
                            double calculatedAmount = operation.getConvertedAmount() * approximateRate;
                            operation.setAmount(calculatedAmount);
                            log.warn("⚠️ Calcul approximatif du montant 'amount' pour l'opération ID: {} → {} (convertedAmount: {} EUR × taux approximatif: {})", 
                                    operation.getId(), calculatedAmount, operation.getConvertedAmount(), approximateRate);
                            log.warn("⚠️ ATTENTION: Ce montant est approximatif car la transaction n'a pas pu être récupérée depuis l'API Guardarian.");
                            return 1;
                        }
                        
                        return -1;
                    }
                } catch (Exception e) {
                    log.error("❌ Erreur lors de la récupération depuis l'API Guardarian pour transaction ID: {}", externalTransactionId, e);
                    
                    // Fallback : calcul approximatif si on a convertedAmount
                    if (operation.getAmount() == null && operation.getConvertedAmount() != null && operation.getConvertedAmount() > 0) {
                        double approximateRate = 1.1;
                        double calculatedAmount = operation.getConvertedAmount() * approximateRate;
                        operation.setAmount(calculatedAmount);
                        log.warn("⚠️ Calcul approximatif du montant 'amount' pour l'opération ID: {} → {} (convertedAmount: {} EUR × taux approximatif: {})", 
                                operation.getId(), calculatedAmount, operation.getConvertedAmount(), approximateRate);
                        return 1;
                    }
                    
                    return -1;
                }
            }
            
            // Si on arrive ici, on a une GuardianTransaction (soit depuis la base, soit depuis l'API)
            GuardarianTransaction tx = txOpt.get();
            log.info("🔍 GuardarianTransaction trouvée pour l'opération ID: {} - toAmount: {}, fromAmountInEur: {}, fromAmount: {}", 
                    operation.getId(), tx.getToAmount(), tx.getFromAmountInEur(), tx.getFromAmount());

            // Corriger les montants
            if (operation.getAmount() == null) {
                // Priorité 1 : Utiliser toAmount si disponible
                if (tx.getToAmount() != null) {
                    operation.setAmount(tx.getToAmount());
                    amountsFixed++;
                    log.info("✅ Montant 'amount' corrigé (toAmount) pour l'opération ID: {} → {}", 
                            operation.getId(), tx.getToAmount());
                } 
                // Priorité 2 : Utiliser expectedToAmount si toAmount est null
                else if (tx.getExpectedToAmount() != null) {
                    operation.setAmount(tx.getExpectedToAmount());
                    amountsFixed++;
                    log.info("✅ Montant 'amount' corrigé (expectedToAmount) pour l'opération ID: {} → {}", 
                            operation.getId(), tx.getExpectedToAmount());
                }
                // Priorité 3 : Calculer à partir de convertedAmount et du taux si disponible
                else if (operation.getConvertedAmount() != null && tx.getEstimatedExchangeRate() != null && tx.getEstimatedExchangeRate() > 0) {
                    double calculatedAmount = operation.getConvertedAmount() / tx.getEstimatedExchangeRate();
                    operation.setAmount(calculatedAmount);
                    amountsFixed++;
                    log.info("✅ Montant 'amount' calculé (convertedAmount / rate) pour l'opération ID: {} → {} (convertedAmount: {}, rate: {})", 
                            operation.getId(), calculatedAmount, operation.getConvertedAmount(), tx.getEstimatedExchangeRate());
                }
                // Priorité 4 : Calculer à partir de convertedAmount et fromAmountInEur si disponible
                else if (operation.getConvertedAmount() != null && tx.getFromAmountInEur() != null) {
                    // Pour ONRAMP : convertedAmount (EUR) → amount (USDC)
                    // Si convertedAmount ≈ fromAmountInEur, on peut utiliser un taux moyen
                    // Taux moyen EUR → USDC : ~1.1
                    double approximateRate = 1.1;
                    double calculatedAmount = operation.getConvertedAmount() * approximateRate;
                    operation.setAmount(calculatedAmount);
                    amountsFixed++;
                    log.warn("⚠️ Montant 'amount' calculé approximativement (convertedAmount × taux moyen) pour l'opération ID: {} → {} (convertedAmount: {} EUR × taux: {})", 
                            operation.getId(), calculatedAmount, operation.getConvertedAmount(), approximateRate);
                    log.warn("⚠️ ATTENTION: Ce montant est approximatif car toAmount et expectedToAmount sont null dans GuardarianTransaction.");
                }
                // Priorité 5 : Utiliser convertedAmount avec taux moyen si disponible
                else if (operation.getConvertedAmount() != null && operation.getConvertedAmount() > 0) {
                    // Pour ONRAMP Guardarian : convertedAmount est en EUR, amount devrait être en USDC
                    // Taux moyen approximatif EUR → USDC : ~1.1
                    double approximateRate = 1.1;
                    double calculatedAmount = operation.getConvertedAmount() * approximateRate;
                    operation.setAmount(calculatedAmount);
                    amountsFixed++;
                    log.warn("⚠️ Montant 'amount' calculé approximativement (convertedAmount × taux moyen) pour l'opération ID: {} → {} (convertedAmount: {} EUR × taux: {})", 
                            operation.getId(), calculatedAmount, operation.getConvertedAmount(), approximateRate);
                    log.warn("⚠️ ATTENTION: Ce montant est approximatif car aucune source fiable n'est disponible dans GuardarianTransaction.");
                } else {
                    log.warn("⚠️ Opération ID: {} a amount=null mais aucune source disponible (toAmount: {}, expectedToAmount: {}, convertedAmount: {}, estimatedExchangeRate: {})", 
                            operation.getId(), null, null, operation.getConvertedAmount(), tx.getEstimatedExchangeRate());
                    return -2;
                }
            } else {
                log.debug("Opération ID: {} a déjà un amount: {}", operation.getId(), operation.getAmount());
            }

            if (operation.getConvertedAmount() == null) {
                if (tx.getFromAmountInEur() != null) {
                    operation.setConvertedAmount(tx.getFromAmountInEur());
                    amountsFixed++;
                    log.info("✅ Montant 'convertedAmount' corrigé (fromAmountInEur) pour l'opération ID: {} → {}", 
                            operation.getId(), tx.getFromAmountInEur());
                } else if (tx.getFromAmount() != null) {
                    operation.setConvertedAmount(tx.getFromAmount());
                    amountsFixed++;
                    log.info("✅ Montant 'convertedAmount' corrigé (fromAmount) pour l'opération ID: {} → {}", 
                            operation.getId(), tx.getFromAmount());
                } else {
                    log.warn("⚠️ Opération ID: {} a convertedAmount=null mais GuardarianTransaction n'a ni fromAmountInEur ni fromAmount", 
                            operation.getId());
                }
            } else {
                log.debug("Opération ID: {} a déjà un convertedAmount: {}", operation.getId(), operation.getConvertedAmount());
            }

            // Corriger le username si nécessaire
            if ((operation.getUsername() == null || operation.getUsername().isEmpty()) 
                    && tx.getUsername() != null) {
                operation.setUsername(tx.getUsername());
                log.info("✅ Username corrigé pour l'opération ID: {} → {}", 
                        operation.getId(), tx.getUsername());
            }

            // Normaliser le statut depuis GuardarianTransaction
            if (tx.getStatus() != null) {
                String normalizedStatus = normalizeStatus(tx.getStatus());
                if (!normalizedStatus.equals(operation.getStatus())) {
                    operation.setStatus(normalizedStatus);
                    log.info("✅ Statut normalisé pour l'opération ID: {} → {}", 
                            operation.getId(), normalizedStatus);
                }
            }

            // Corriger la devise si nécessaire
            if ((operation.getDevise() == null || operation.getDevise().isEmpty()) 
                    && tx.getToCurrency() != null) {
                operation.setDevise(tx.getToCurrency());
                log.info("✅ Devise corrigée pour l'opération ID: {} → {}", 
                        operation.getId(), tx.getToCurrency());
            }

        } catch (Exception e) {
            log.error("❌ Erreur lors de la correction de l'opération Guardarian ID: {}", 
                    operation.getId(), e);
        }
        return amountsFixed;
    }

    /**
     * Normalise le statut Guardarian vers les statuts Operation standardisés
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
                // Si le statut est déjà un statut valide, le retourner tel quel
                if (VALID_STATUSES.contains(guardarianStatus)) {
                    yield guardarianStatus;
                }
                log.warn("⚠️ Statut non reconnu: {}, utilisation de 'NEW' par défaut", guardarianStatus);
                yield "NEW";
            }
        };
    }
}

