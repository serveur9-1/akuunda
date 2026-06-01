package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldTransactionDetails;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMeldServiceClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/internal/v1/meld/webhook")
@Tag(name = "Akuunda Services - Meld Webhook",
        description = "Endpoint pour recevoir les notifications de transaction Meld via webhook")
@RequiredArgsConstructor
public class AkuundaMeldWebhookController {

    private final AkuundaMeldServiceClient meldServiceClient;
    private final OperationRepository operationRepository;
    private final TransactionNotificationHelper notificationHelper;

    @PostMapping
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Recevoir les mises à jour de transactions Meld",
            description = """
                    Reçoit les webhooks Meld relayés par generelinkYC.
                    Events supportés :
                    - TRANSACTION_CRYPTO_COMPLETE → statut VALIDEE + notification succès
                    - TRANSACTION_CRYPTO_FAILED → statut ECHOUEE + notification échec
                    - TRANSACTION_CRYPTO_PENDING / TRANSFERRING → statut EN_COURS + notification pending
                    - TRANSACTION_FIAT_CREATED / UPDATED → statut EN_COURS (log)
                    - TRANSACTION_FIAT_UPDATED (SETTLED) → statut VALIDEE
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Webhook traité avec succès"),
                    @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
            }
    )
    public ResponseEntity<String> handleMeldWebhook(@RequestBody Map<String, Object> payload) {

        try {
            String transactionId      = (String) payload.get("transactionId");
            String externalCustomerId = (String) payload.get("externalCustomerId");
            String status             = (String) payload.get("status");
            String eventType          = (String) payload.get("eventType");
            String eventId            = (String) payload.get("eventId");
            String sessionId          = (String) payload.get("sessionId");

            log.info("📩 Webhook Meld reçu: eventType={}, transactionId={}, sessionId={}, status={}, customer={}",
                    eventType, transactionId, sessionId, status, externalCustomerId);

            if (transactionId == null || transactionId.isBlank()) {
                log.warn("⚠️ Webhook Meld sans transactionId (eventType={})", eventType);
                return ResponseEntity.ok("Ignoré (pas de transactionId)");
            }

            // ══════════════════════════════════════════════════════════════
            // 🔎 TROUVER L'OPÉRATION EN BASE
            // ══════════════════════════════════════════════════════════════
            Operation operation = operationRepository.findByOperationHash(transactionId);

            if (operation == null && sessionId != null && !sessionId.isBlank()) {
                operation = operationRepository.findByOperationHash(sessionId);
                if (operation != null) {
                    log.info("✅ Opération Meld trouvée via sessionId: sessionId={}, opHash={}",
                            sessionId, operation.getOperationHash());
                }
            }

            if (operation == null && externalCustomerId != null && !externalCustomerId.isBlank()) {
                try {
                    operation = operationRepository.findFirstByUsernameAndDesignationOrderByCreatedAtDesc(
                            externalCustomerId, "MELD_ON_RAMP");
                    if (operation != null) {
                        log.info("✅ Opération Meld trouvée via fallback username: customer={}, opHash={}",
                                externalCustomerId, operation.getOperationHash());
                    }
                } catch (Exception fallbackEx) {
                    log.warn("⚠️ Erreur fallback Meld par externalCustomerId (non bloquant): {}", fallbackEx.getMessage());
                }
            }

            // ── Events intermédiaires — mise à jour statut EN_COURS + log ──
            if (isIntermediateEvent(eventType, status)) {
                log.info("ℹ️ Meld event intermédiaire: eventType={}, status={}, transactionId={}",
                        eventType, status, transactionId);

                if (operation != null && !isTerminalStatus(operation.getStatus())) {
                    String previousStatus = operation.getStatus();
                    if (!"EN_COURS".equals(previousStatus)) {
                        operation.setStatus("EN_COURS");
                        operation.setUpdatedAt(LocalDateTime.now());
                        operationRepository.saveAndFlush(operation);
                        log.info("✅ Opération Meld mise à jour: {} → EN_COURS pour {} (opHash={})",
                                previousStatus, transactionId, operation.getOperationHash());

                        try {
                            String amount = formatAmount(operation);
                            notificationHelper.notifyTransactionPending(
                                    operation.getUsername(), amount, transactionId);
                            log.info("🔔 Notification Meld PENDING envoyée à {}", operation.getUsername());
                        } catch (Exception e) {
                            log.warn("⚠️ Notification pending échouée (non bloquant): {}", e.getMessage());
                        }
                    }
                } else if (operation == null) {
                    log.warn("⚠️ Opération non trouvée pour Meld intermédiaire: transactionId={}, sessionId={}, customer={}",
                            transactionId, sessionId, externalCustomerId);
                }

                return ResponseEntity.ok("Event intermédiaire enregistré");
            }

            // ── Events terminaux — essayer de récupérer les détails complets ──
            String resolvedStatus = status;
            MeldTransactionDetails meldDetails = null; // ✅ AJOUT : stocker les détails Meld

            try {
                ResponseEntity<MeldTransactionDetails> detailsResponse =
                        meldServiceClient.getTransactionDetails(transactionId);

                if (detailsResponse.getStatusCode().is2xxSuccessful() && detailsResponse.getBody() != null) {
                    meldDetails = detailsResponse.getBody(); // ✅ AJOUT : garder la référence
                    if (resolvedStatus == null) {
                        resolvedStatus = meldDetails.getStatus();
                    }
                    log.info("✅ Détails Meld récupérés via API: transactionId={}, apiStatus={}, destinationAmount={}, destinationCurrency={}",
                            transactionId, meldDetails.getStatus(), meldDetails.getDestinationAmount(), meldDetails.getDestinationCurrencyCode());
                } else {
                    log.warn("⚠️ API Meld détails n'a pas retourné de résultat pour {} — utilisation du statut payload: {}",
                            transactionId, resolvedStatus);
                }
            } catch (Exception detailsEx) {
                log.warn("⚠️ Erreur appel API Meld détails pour {} (non bloquant): {} — utilisation du statut payload: {}",
                        transactionId, detailsEx.getMessage(), resolvedStatus);
            }

            // Fallback : mapper par eventType si toujours pas de statut
            if (resolvedStatus == null) {
                resolvedStatus = mapEventTypeToStatus(eventType);
                log.info("ℹ️ Statut résolu via eventType: {} → {}", eventType, resolvedStatus);
            }

            // ══════════════════════════════════════════════════════════════
            // 📝 MISE À JOUR STATUT OPÉRATION + ENRICHISSEMENT + NOTIFICATION
            // ══════════════════════════════════════════════════════════════
            try {
                if (operation != null && operation.getUsername() != null) {
                    String username = operation.getUsername();
                    String previousStatus = operation.getStatus();

                    if (isCompleted(eventType, resolvedStatus)) {
                        if (!"VALIDEE".equals(previousStatus)) {

                            // ✅ AJOUT : Enrichir providerAmount/providerDevise depuis Meld API
                            enrichOperationFromMeldDetails(operation, meldDetails);

                            operation.setStatus("VALIDEE");
                            operation.setUpdatedAt(LocalDateTime.now());
                            operationRepository.saveAndFlush(operation);
                            log.info("✅ Opération Meld mise à jour: {} → VALIDEE pour {} (opHash={}, providerAmount={}, providerDevise={})",
                                    previousStatus, transactionId, operation.getOperationHash(),
                                    operation.getProviderAmount(), operation.getProviderDevise());

                            String amount = formatAmount(operation);
                            notificationHelper.notifyTransactionSuccess(
                                    username,
                                    "Fonds reçus : " + amount,
                                    transactionId);
                            log.info("🔔 Notification Meld COMPLETED envoyée à {}", username);
                        } else {
                            log.info("ℹ️ Opération déjà VALIDEE, doublon ignoré pour {}", transactionId);
                        }

                    } else if (isFailed(eventType, resolvedStatus)) {
                        if (!"ECHOUEE".equals(previousStatus)) {
                            operation.setStatus("ECHOUEE");
                            operation.setUpdatedAt(LocalDateTime.now());
                            operationRepository.saveAndFlush(operation);
                            log.info("✅ Opération Meld mise à jour: {} → ECHOUEE pour {} (opHash={})",
                                    previousStatus, transactionId, operation.getOperationHash());

                            notificationHelper.notifyTransactionFailed(
                                    username,
                                    "Transaction échouée",
                                    "Votre transaction Meld n'a pas abouti.",
                                    transactionId);
                            log.info("🔔 Notification Meld FAILED envoyée à {}", username);
                        } else {
                            log.info("ℹ️ Opération déjà ECHOUEE, doublon ignoré pour {}", transactionId);
                        }
                    } else {
                        log.info("ℹ️ Event Meld terminal non classifié: eventType={}, resolvedStatus={}, transactionId={}",
                                eventType, resolvedStatus, transactionId);
                    }
                } else {
                    log.warn("⚠️ Opération non trouvée pour Meld terminal: transactionId={}, sessionId={}, customer={}",
                            transactionId, sessionId, externalCustomerId);
                }
            } catch (Exception e) {
                log.warn("⚠️ Notification push Meld échouée (non bloquant): {}", e.getMessage());
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("❌ Erreur traitement webhook Meld", e);
            return ResponseEntity.status(500).body("Erreur interne");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * ✅ AJOUT : Enrichit l'opération avec les données Meld API
     * - destinationAmount → providerAmount (montant crypto USDC reçu)
     * - destinationCurrencyCode → providerDevise (ex: USDC)
     * - sourceAmount → montant fiat original (vérifie cohérence avec amount)
     */
    private void enrichOperationFromMeldDetails(Operation operation, MeldTransactionDetails meldDetails) {
        if (meldDetails == null) {
            log.info("ℹ️ Pas de détails Meld API — enrichissement ignoré");
            return;
        }

        // providerAmount = montant crypto destination (USDC reçu sur le wallet)
        if (operation.getProviderAmount() == null && meldDetails.getDestinationAmount() != null) {
            operation.setProviderAmount(meldDetails.getDestinationAmount().doubleValue());
            log.info("✅ Enrichi providerAmount = {} depuis Meld API", meldDetails.getDestinationAmount());
        }

        // providerDevise = devise crypto destination (USDC)
        if (operation.getProviderDevise() == null && meldDetails.getDestinationCurrencyCode() != null) {
            operation.setProviderDevise(meldDetails.getDestinationCurrencyCode());
            log.info("✅ Enrichi providerDevise = {} depuis Meld API", meldDetails.getDestinationCurrencyCode());
        } else if (operation.getProviderDevise() == null) {
            operation.setProviderDevise("USDC");
        }

        // convertedAmount = montant USDC (pour cohérence avec les autres providers)
        if (operation.getConvertedAmount() == null && meldDetails.getDestinationAmount() != null) {
            operation.setConvertedAmount(meldDetails.getDestinationAmount().doubleValue());
        }

        // Vérifier/enrichir le montant fiat source si incohérent
        if (meldDetails.getSourceAmount() != null && operation.getAmount() != null) {
            double meldSource = meldDetails.getSourceAmount().doubleValue();
            if (Math.abs(meldSource - operation.getAmount()) > 0.01) {
                log.info("ℹ️ Montant Meld source ({} {}) diffère de amount en base ({} {}) — conserve la valeur en base",
                        meldSource, meldDetails.getSourceCurrencyCode(),
                        operation.getAmount(), operation.getDevise());
            }
        }
    }

    private boolean isTerminalStatus(String status) {
        return "VALIDEE".equals(status) || "ECHOUEE".equals(status) || "REMBOURSEE".equals(status);
    }

    private boolean isIntermediateEvent(String eventType, String status) {
        if (eventType == null) return false;
        return switch (eventType) {
            case "TRANSACTION_FIAT_CREATED" -> true;
            case "TRANSACTION_CRYPTO_PENDING" -> true;
            case "TRANSACTION_CRYPTO_TRANSFERRING" -> true;
            case "TRANSACTION_FIAT_UPDATED" -> {
                if (status != null) {
                    String s = status.toUpperCase();
                    yield !s.equals("SETTLED") && !s.equals("FAILED") && !s.equals("DECLINED")
                            && !s.equals("CANCELLED") && !s.equals("ERROR");
                }
                yield true;
            }
            default -> false;
        };
    }

    private boolean isCompleted(String eventType, String status) {
        if ("TRANSACTION_CRYPTO_COMPLETE".equals(eventType)) return true;
        if (status == null) return false;
        String s = status.toUpperCase();
        return s.equals("SETTLED") || s.equals("ONRAMP_SETTLED") || s.equals("COMPLETED");
    }

    private boolean isFailed(String eventType, String status) {
        if ("TRANSACTION_CRYPTO_FAILED".equals(eventType)) return true;
        if (status == null) return false;
        String s = status.toUpperCase();
        return s.equals("FAILED") || s.equals("DECLINED") || s.equals("CANCELLED")
                || s.equals("ERROR") || s.equals("REFUNDED") || s.equals("VOIDED");
    }

    private String mapEventTypeToStatus(String eventType) {
        if (eventType == null) return "UNKNOWN";
        return switch (eventType) {
            case "TRANSACTION_CRYPTO_COMPLETE" -> "SETTLED";
            case "TRANSACTION_CRYPTO_FAILED" -> "FAILED";
            case "TRANSACTION_CRYPTO_PENDING" -> "PENDING";
            case "TRANSACTION_CRYPTO_TRANSFERRING" -> "PROCESSING";
            case "TRANSACTION_FIAT_CREATED" -> "PENDING_CREATED";
            case "TRANSACTION_FIAT_UPDATED" -> "PROCESSING";
            default -> "UNKNOWN";
        };
    }

    private String formatAmount(Operation operation) {
        if (operation.getAmount() != null && operation.getDevise() != null) {
            return String.format("%,.0f %s", operation.getAmount(), operation.getDevise());
        }
        if (operation.getProviderAmount() != null) {
            return String.format("%.2f %s", operation.getProviderAmount(),
                    operation.getProviderDevise() != null ? operation.getProviderDevise() : "USDC");
        }
        return "montant inconnu";
    }
}
