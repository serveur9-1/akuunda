package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = "/api/internal/v1/yellowcard-webhook", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - YellowCard Event Webhook",
        description = "Reçoit TOUS les events YellowCard (PENDING, FAILED, CANCELLED, REFUNDED, SETTLEMENT, PAYMENT.*) relayés par generelinkYC")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class YellowCardEventWebhookController {

    private final OperationRepository operationRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final PermanentLinkSessionRepository permanentLinkSessionRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final CurrencyFreaksClientService currencyFreaksClientService;
    private final TransactionNotificationHelper notificationHelper;

    /**
     * Designations qui ont DÉJÀ leur propre système de notification.
     */
    private static final Set<String> SKIP_NOTIFICATION_DESIGNATIONS = Set.of(
            "ONE_TIME_PAYMENT_RECEIVED",
            "INTERNAL_TRANSFER_SENT",
            "INTERNAL_TRANSFER_RECEIVED"
    );

    @PostMapping("/event")
    @io.swagger.v3.oas.annotations.Operation(
            operationId = "yellowCardEvent",
            summary = "Recevoir un event YellowCard (COLLECTION ou PAYMENT)",
            description = "Relayé par generelinkYC pour TOUS les events : PENDING, FAILED, CANCELLED, REFUNDED, COMPLETED, SETTLEMENT, ACTION_REQUIRED."
    )
    public ResponseEntity<Map<String, Object>> handleEvent(@RequestBody Map<String, Object> payload) {
        log.info("📩 YellowCard event reçu: {}", payload);

        try {
            String sequenceId     = str(payload, "sequenceId");
            String status         = str(payload, "status");
            String eventType      = str(payload, "eventType");
            String type           = str(payload, "type");
            String yellowCardTxId = str(payload, "yellowCardTxId");

            if (sequenceId == null || sequenceId.isBlank()) {
                log.warn("⚠️ Event sans sequenceId, ignoré");
                return ok(false, "sequenceId manquant");
            }

            // ── Trouver l'opération en base ──
            Operation operation = operationRepository.findByOperationHash(sequenceId);

            // ── Fallback 1 : chercher via OneTimePaymentLink.ycSequenceId ──
            if (operation == null) {
                try {
                    Optional<OneTimePaymentLink> linkOpt = oneTimePaymentLinkRepository.findByYcSequenceId(sequenceId);
                    if (linkOpt.isPresent()) {
                        OneTimePaymentLink link = linkOpt.get();
                        String creatorUsername = link.getCreator() != null ? link.getCreator().getUsername() : null;
                        if (creatorUsername != null) {
                            operation = operationRepository.findFirstByUsernameAndDesignationOrderByCreatedAtDesc(
                                    creatorUsername, "ONE_TIME_PAYMENT_RECEIVED");
                        }
                        if (operation != null) {
                            log.info("✅ Opération trouvée via fallback OneTimePaymentLink: sequenceId={}, opHash={}",
                                    sequenceId, operation.getOperationHash());
                        }
                    }
                } catch (Exception fallbackEx) {
                    log.warn("⚠️ Erreur fallback OneTimePaymentLink (non bloquant): {}", fallbackEx.getMessage());
                }
            }

            // ── Fallback 2 : session permanent link (external_transaction_id = sequenceId YellowCard) ──
            if (operation == null) {
                try {
                    Optional<PermanentLinkSession> sessOpt = permanentLinkSessionRepository.findByExternalTransactionId(sequenceId);
                    if (sessOpt.isPresent()) {
                        PermanentLinkSession pls = sessOpt.get();
                        if (pls.getPermanentLink() != null && pls.getPermanentLink().getCreator() != null) {
                            String creatorUser = pls.getPermanentLink().getCreator().getUsername();
                            operation = operationRepository.findByOperationHash(sequenceId);
                            if (operation == null) {
                                operation = createPendingCollectionOperationFromPermanentLinkSession(pls, sequenceId, creatorUser);
                                log.info("✅ Opération PENDING créée via fallback PermanentLinkSession: sequenceId={}, creator={}",
                                        sequenceId, creatorUser);
                            } else {
                                log.info("✅ Opération déjà présente pour sequenceId {} (permanent link session)", sequenceId);
                            }
                        }
                    }
                } catch (Exception fallbackSessionEx) {
                    log.warn("⚠️ Erreur fallback PermanentLinkSession (non bloquant): {}", fallbackSessionEx.getMessage());
                }
            }

            // ══════════════════════════════════════════════════════════════
            // OPÉRATION NON TROUVÉE → création automatique si COMPLETED
            // ══════════════════════════════════════════════════════════════
            if (operation == null) {
                String internalStatusCheck = mapYellowCardStatus(status, eventType);
                Double settledAmount = dbl(payload, "settledAmount");

                // Si COMPLETED avec un montant → créer l'opération automatiquement
                if ("VALIDEE".equals(internalStatusCheck) && settledAmount != null && settledAmount > 0) {
                    boolean isPayment = "PAYMENT".equalsIgnoreCase(type);
                    log.info("🆕 Opération non trouvée mais COMPLETED avec settledAmount={}, type={} → création auto pour sequenceId={}",
                            settledAmount, isPayment ? "PAYMENT (retrait)" : "COLLECTION (dépôt)", sequenceId);

                    if (isPayment) {
                        return createPaymentOperationFromWebhook(payload, sequenceId, settledAmount);
                    } else {
                        return createCollectionOperationFromWebhook(payload, sequenceId, settledAmount);
                    }
                }

                log.warn("⚠️ Aucune opération trouvée pour sequenceId={}, eventType={}, status={}",
                        sequenceId, eventType, status);
                return ok(false, "Opération non trouvée pour sequenceId=" + sequenceId);
            }

            // ══════════════════════════════════════════════════════════════
            // OPÉRATION TROUVÉE → mise à jour du statut
            // ══════════════════════════════════════════════════════════════
            String previousStatus = operation.getStatus();
            String username = operation.getUsername();
            String designation = operation.getDesignation();
            String internalStatus = mapYellowCardStatus(status, eventType);

            // Idempotence
            if (internalStatus.equals(previousStatus)) {
                log.info("ℹ️ Statut identique ({}), doublon ignoré pour {}", internalStatus, sequenceId);
                return ok(true, "Déjà à jour");
            }

            // Ne pas régresser un statut terminal
            if (isTerminal(previousStatus) && !isTerminal(internalStatus)) {
                log.info("ℹ️ Statut terminal ({}) ne peut pas régresser vers {}, ignoré pour {}",
                        previousStatus, internalStatus, sequenceId);
                return ok(true, "Statut terminal non régressé");
            }

            // ── Si VALIDEE : mettre à jour les montants réels depuis le webhook AVANT de créditer ──
            if ("VALIDEE".equals(internalStatus)) {
                Double settledAmount = dbl(payload, "settledAmount");
                if (settledAmount != null && settledAmount > 0) {
                    double oldProviderAmount = operation.getProviderAmount() != null ? operation.getProviderAmount() : 0;
                    if (Math.abs(oldProviderAmount - settledAmount) > 0.001) {
                        log.info("🔄 Correction providerAmount avec settledAmount réel (frais YC déduits): {} → {} pour {}",
                                oldProviderAmount, settledAmount, sequenceId);
                        operation.setProviderAmount(settledAmount);
                    }
                }
            }

            // Mettre à jour l'opération
            operation.setStatus(internalStatus);
            operation.setUpdatedAt(LocalDateTime.now());
            operationRepository.saveAndFlush(operation);
            log.info("✅ Opération mise à jour: {} → {} pour {}", previousStatus, internalStatus, sequenceId);

            // ── Si retrait ECHOUÉ → rembourser le wallet ──
            if ("ECHOUEE".equals(internalStatus)
                    && "DEBIT".equals(operation.getType())
                    && ("PENDING".equals(previousStatus) || "EN_COURS".equals(previousStatus))) {
                refundWalletAfterFailedWithdrawal(operation);
            }

            // ── Si remboursement confirmé → rembourser le wallet aussi ──
            if ("REMBOURSEE".equals(internalStatus)
                    && "DEBIT".equals(operation.getType())
                    && !isTerminal(previousStatus)) {
                refundWalletAfterFailedWithdrawal(operation);
            }

            // ── Si dépôt VALIDEE et wallet pas encore crédité (status était PENDING ou EN_COURS) ──
            if ("VALIDEE".equals(internalStatus)
                    && "CREDIT".equals(operation.getType())
                    && ("PENDING".equals(previousStatus) || "EN_COURS".equals(previousStatus))) {
                creditWalletOnCompletedDeposit(operation);
            }

            // ── Notification push ──
            boolean shouldNotify = designation == null
                    || !SKIP_NOTIFICATION_DESIGNATIONS.contains(designation);

            if (shouldNotify) {
                sendNotification(username, internalStatus, operation, type, sequenceId);
            } else {
                log.info("ℹ️ Notification skippée pour designation={} (gérée par son propre controller)", designation);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Event traité");
            result.put("sequenceId", sequenceId);
            result.put("previousStatus", previousStatus);
            result.put("newStatus", internalStatus);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Erreur traitement event YellowCard", e);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("success", false);
            errorBody.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return ResponseEntity.internalServerError().body(errorBody);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Création d'opération COLLECTION (dépôt OnRamp) depuis webhook
    // ═══════════════════════════════════════════════════════════════════════

    private ResponseEntity<Map<String, Object>> createCollectionOperationFromWebhook(
            Map<String, Object> payload, String sequenceId, Double settledAmountUSDC) {

        try {
            String username = resolveUsername(payload, sequenceId, "YELLOWCARD_ON_RAMP");
            if (username == null) {
                log.warn("⚠️ Impossible de trouver l'utilisateur pour dépôt sequenceId={}", sequenceId);
                return ok(false, "Utilisateur non trouvé pour créer l'opération de dépôt");
            }

            Wallet wallet = walletRepository.findByUsersUsername(username);
            if (wallet == null) {
                log.warn("⚠️ Wallet non trouvé pour {}", username);
                return ok(false, "Wallet non trouvé pour " + username);
            }

            String walletCurrency = resolveWalletCurrency(username);
            double amountLocal = resolveLocalAmount(payload, settledAmountUSDC, walletCurrency);

            // Créditer le wallet
            double balanceBefore = wallet.getBalance();
            double deviseBalanceBefore = wallet.getDeviseBalance();
            wallet.setBalance(balanceBefore + settledAmountUSDC);
            wallet.setDeviseBalance(deviseBalanceBefore + amountLocal);
            walletRepository.saveAndFlush(wallet);

            log.info("✅ Wallet crédité (dépôt): username={}, balance USDC: {} → {} (+{}), deviseBalance: {} → {} (+{} {})",
                    username, balanceBefore, wallet.getBalance(), settledAmountUSDC,
                    deviseBalanceBefore, wallet.getDeviseBalance(), amountLocal, walletCurrency);

            // Créer l'opération CREDIT
            Operation operation = new Operation();
            operation.setType("CREDIT");
            operation.setStatus("VALIDEE");
            operation.setUsername(username);
            operation.setDevise(walletCurrency);
            operation.setAmount(amountLocal);
            operation.setConvertedAmount(settledAmountUSDC);
            operation.setOriginalAmount(amountLocal);
            operation.setOriginalDevise(walletCurrency);
            operation.setDesignation("YELLOWCARD_ON_RAMP");
            operation.setTransactionType("Dépôt");
            operation.setProvider("YellowCard");
            operation.setProviderAmount(settledAmountUSDC);
            operation.setProviderDevise(str(payload, "settledCurrency") != null ? str(payload, "settledCurrency") : "USDC");
            operation.setOperationHash(sequenceId);
            operation.setCreatedAt(LocalDateTime.now());
            operation.setUpdatedAt(LocalDateTime.now());
            operation = operationRepository.save(operation);

            log.info("✅ Opération CREDIT (dépôt) créée: id={}, hash={}, {} {} (provider: {} USDC)",
                    operation.getId(), sequenceId, amountLocal, walletCurrency, settledAmountUSDC);

            // Notification
            notifySafe(username, String.format("Dépôt reçu : %,.2f %s", amountLocal, walletCurrency), sequenceId);

            return buildCreatedResponse(operation, sequenceId, settledAmountUSDC, amountLocal, walletCurrency);

        } catch (Exception e) {
            return handleCreationError(e, sequenceId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Création d'opération PAYMENT (retrait OffRamp) depuis webhook
    // ═══════════════════════════════════════════════════════════════════════

    private ResponseEntity<Map<String, Object>> createPaymentOperationFromWebhook(
            Map<String, Object> payload, String sequenceId, Double settledAmountUSDC) {

        try {
            String username = resolveUsername(payload, sequenceId, "YELLOWCARD_OFF_RAMP");
            if (username == null) {
                log.warn("⚠️ Impossible de trouver l'utilisateur pour retrait sequenceId={}", sequenceId);
                return ok(false, "Utilisateur non trouvé pour créer l'opération de retrait");
            }

            Wallet wallet = walletRepository.findByUsersUsername(username);
            if (wallet == null) {
                log.warn("⚠️ Wallet non trouvé pour {}", username);
                return ok(false, "Wallet non trouvé pour " + username);
            }

            String walletCurrency = resolveWalletCurrency(username);
            double amountLocal = resolveLocalAmount(payload, settledAmountUSDC, walletCurrency);

            // Récupérer le montant et la devise du recipient
            String recipientPhone = str(payload, "recipientPhone");
            String recipientName = str(payload, "recipientName");

            // NE PAS débiter le wallet ici si l'opération n'existait pas
            // Car normalement le débit se fait à l'initiation dans updateWalletAfterOffRamp()
            // Si on arrive ici c'est que le wallet a DÉJÀ été débité à l'initiation
            // On crée juste l'opération pour le suivi

            // Créer l'opération DEBIT
            Operation operation = new Operation();
            operation.setType("DEBIT");
            operation.setStatus("VALIDEE");
            operation.setUsername(username);
            operation.setDevise(walletCurrency);
            operation.setAmount(amountLocal);
            operation.setConvertedAmount(settledAmountUSDC);
            operation.setOriginalAmount(amountLocal);
            operation.setOriginalDevise(walletCurrency);
            operation.setDesignation("YELLOWCARD_OFF_RAMP");
            operation.setTransactionType("Retrait");
            operation.setProvider("YellowCard");
            operation.setProviderAmount(settledAmountUSDC);
            operation.setProviderDevise(str(payload, "settledCurrency") != null ? str(payload, "settledCurrency") : "USDC");
            operation.setOperationHash(sequenceId);
            operation.setCounterpartDisplayName(recipientName);
            operation.setCreatedAt(LocalDateTime.now());
            operation.setUpdatedAt(LocalDateTime.now());
            operation = operationRepository.save(operation);

            log.info("✅ Opération DEBIT (retrait) créée: id={}, hash={}, {} {} (provider: {} USDC), recipient={}",
                    operation.getId(), sequenceId, amountLocal, walletCurrency, settledAmountUSDC, recipientName);

            // Notification
            notifySafe(username, String.format("Retrait confirmé : %,.2f %s", amountLocal, walletCurrency), sequenceId);

            return buildCreatedResponse(operation, sequenceId, settledAmountUSDC, amountLocal, walletCurrency);

        } catch (Exception e) {
            return handleCreationError(e, sequenceId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Crédit wallet quand un dépôt passe de PENDING → VALIDEE
    // ═══════════════════════════════════════════════════════════════════════

    private void creditWalletOnCompletedDeposit(Operation operation) {
        try {
            String username = operation.getUsername();
            if (username == null || username.isBlank()) return;

            Wallet wallet = walletRepository.findByUsersUsername(username);
            if (wallet == null) {
                log.warn("⚠️ Wallet non trouvé pour {}, impossible de créditer", username);
                return;
            }

            double providerAmount = operation.getProviderAmount() != null ? operation.getProviderAmount() : 0;
            double amount = operation.getAmount() != null ? operation.getAmount() : 0;

            if (providerAmount <= 0 && amount <= 0) {
                log.warn("⚠️ Montants nuls sur opération id={}, pas de crédit possible", operation.getId());
                return;
            }

            double balanceBefore = wallet.getBalance();
            double deviseBalanceBefore = wallet.getDeviseBalance();

            if (providerAmount > 0) {
                wallet.setBalance(balanceBefore + providerAmount);
            }
            if (amount > 0) {
                wallet.setDeviseBalance(deviseBalanceBefore + amount);
            }

            walletRepository.saveAndFlush(wallet);

            log.info("✅ Wallet crédité après dépôt VALIDEE: username={}, balance USDC: {} → {} (+{}), deviseBalance: {} → {} (+{})",
                    username, balanceBefore, wallet.getBalance(), providerAmount,
                    deviseBalanceBefore, wallet.getDeviseBalance(), amount);

        } catch (Exception e) {
            log.error("❌ Erreur crédit wallet pour opération id={}: {}", operation.getId(), e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Remboursement wallet après échec retrait
    // ═══════════════════════════════════════════════════════════════════════

    private void refundWalletAfterFailedWithdrawal(Operation operation) {
        try {
            String username = operation.getUsername();
            if (username == null || username.isBlank()) {
                log.warn("⚠️ Username manquant sur opération id={}, impossible de rembourser", operation.getId());
                return;
            }

            Wallet wallet = walletRepository.findByUsersUsername(username);
            if (wallet == null) {
                log.warn("⚠️ Wallet non trouvé pour {}, impossible de rembourser", username);
                return;
            }

            double amount = operation.getAmount() != null ? operation.getAmount() : 0;
            double providerAmount = operation.getProviderAmount() != null ? operation.getProviderAmount() : 0;

            if (amount <= 0 && providerAmount <= 0) {
                log.warn("⚠️ Montants nuls sur opération id={}, pas de remboursement possible", operation.getId());
                return;
            }

            double balanceBefore = wallet.getBalance();
            double deviseBalanceBefore = wallet.getDeviseBalance();

            if (providerAmount > 0) wallet.setBalance(balanceBefore + providerAmount);
            if (amount > 0) wallet.setDeviseBalance(deviseBalanceBefore + amount);

            walletRepository.saveAndFlush(wallet);

            log.info("💰 Wallet remboursé après échec retrait: username={}, balance USDC: {} → {} (+{}), deviseBalance: {} → {} (+{})",
                    username, balanceBefore, wallet.getBalance(), providerAmount,
                    deviseBalanceBefore, wallet.getDeviseBalance(), amount);

        } catch (Exception e) {
            log.error("❌ Erreur remboursement wallet pour opération id={}: {}", operation.getId(), e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers communs
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Résout le username depuis le payload ou par recherche en base.
     */
    private String resolveUsername(Map<String, Object> payload, String sequenceId, String designation) {
        // 1. recipientPhone (retrait)
        String recipientPhone = str(payload, "recipientPhone");
        if (recipientPhone != null && !recipientPhone.isBlank()) {
            if (walletRepository.findByUsersUsername(recipientPhone) != null) return recipientPhone;
        }

        // 2. payerPhone (dépôt)
        String payerPhone = str(payload, "payerPhone");
        if (payerPhone != null && !payerPhone.isBlank()) {
            if (walletRepository.findByUsersUsername(payerPhone) != null) return payerPhone;
        }

        // 3. Chercher par opération PENDING récente du même type
        try {
            LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
            List<Operation> recentOps = operationRepository
                    .findByDesignationAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                            designation, "PENDING", twoHoursAgo);
            if (recentOps != null && !recentOps.isEmpty()) {
                log.info("🔎 Trouvé {} opération(s) PENDING {} récente(s), utilisation de la plus récente",
                        recentOps.size(), designation);
                return recentOps.get(0).getUsername();
            }

            // 4. Chercher aussi EN_COURS
            List<Operation> enCoursOps = operationRepository
                    .findByDesignationAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                            designation, "EN_COURS", twoHoursAgo);
            if (enCoursOps != null && !enCoursOps.isEmpty()) {
                log.info("🔎 Trouvé {} opération(s) EN_COURS {} récente(s)", enCoursOps.size(), designation);
                return enCoursOps.get(0).getUsername();
            }
        } catch (Exception e) {
            log.warn("⚠️ Erreur recherche username par opération récente: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Résout la devise locale de l'utilisateur.
     */
    private String resolveWalletCurrency(String username) {
        try {
            Users user = userRepository.getUsersByUsername(username);
            if (user != null && user.getCountryCurrency() != null
                    && user.getCountryCurrency().getCurrencyCode() != null) {
                return user.getCountryCurrency().getCurrencyCode();
            }
        } catch (Exception e) {
            log.warn("⚠️ Erreur résolution devise pour {}: {}", username, e.getMessage());
        }
        return "USD";
    }

    /**
     * Résout le montant en devise locale.
     * Priorité : localAmount du payload > conversion USDC → devise locale.
     */
    private double resolveLocalAmount(Map<String, Object> payload, Double settledAmountUSDC, String walletCurrency) {
        // 1. localAmount du payload déjà dans la bonne devise
        Double localAmount = dbl(payload, "localAmount");
        String localCurrency = str(payload, "localCurrency");
        if (localAmount != null && localAmount > 0
                && localCurrency != null && localCurrency.equalsIgnoreCase(walletCurrency)) {
            log.info("✅ Utilisation du localAmount du payload: {} {}", localAmount, walletCurrency);
            return localAmount;
        }

        // 2. Convertir depuis USD
        if (!"USD".equalsIgnoreCase(walletCurrency) && !"USDC".equalsIgnoreCase(walletCurrency)) {
            try {
                var conv = currencyFreaksClientService.convertCurrency("USD", walletCurrency, settledAmountUSDC);
                if (conv != null && conv.getStatusCode().is2xxSuccessful() && conv.getBody() != null) {
                    String convertedStr = conv.getBody().getConvertedAmount();
                    if (convertedStr != null && !convertedStr.isBlank()) {
                        double converted = Double.parseDouble(convertedStr);
                        log.info("✅ Conversion USD → {}: {} → {}", walletCurrency, settledAmountUSDC, converted);
                        return converted;
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Conversion échouée, utilisation montant USDC: {}", e.getMessage());
            }
        }

        return settledAmountUSDC;
    }

    private void sendNotification(String username, String internalStatus, Operation operation, String type, String sequenceId) {
        try {
            switch (internalStatus) {
                case "ECHOUEE" -> {
                    String refundMsg = "DEBIT".equals(operation.getType())
                            ? "Votre retrait YellowCard a échoué. Votre solde a été restauré."
                            : "Votre transaction YellowCard n'a pas abouti.";
                    notificationHelper.notifyTransactionFailed(username, "Transaction échouée", refundMsg, sequenceId);
                    log.info("🔔 Notification ECHOUEE envoyée à {}", username);
                }
                case "VALIDEE" -> {
                    String amount = formatAmount(operation, type);
                    notificationHelper.notifyTransactionSuccess(username, amount, sequenceId);
                    log.info("🔔 Notification VALIDEE envoyée à {}", username);
                }
                case "REMBOURSEE" -> {
                    notificationHelper.notifyTransactionSuccess(username, "Remboursement effectué. Votre solde a été restauré.", sequenceId);
                    log.info("🔔 Notification REMBOURSEE envoyée à {}", username);
                }
                case "EN_COURS" -> {
                    String amount = formatAmount(operation, type);
                    notificationHelper.notifyTransactionPending(username, amount, sequenceId);
                    log.info("🔔 Notification EN_COURS envoyée à {}", username);
                }
                default -> log.debug("Pas de notification pour statut: {}", internalStatus);
            }
        } catch (Exception e) {
            log.warn("⚠️ Notification push échouée (non bloquant): {}", e.getMessage());
        }
    }

    private void notifySafe(String username, String message, String sequenceId) {
        try {
            notificationHelper.notifyTransactionSuccess(username, message, sequenceId);
            log.info("🔔 Notification envoyée à {}: {}", username, message);
        } catch (Exception e) {
            log.warn("⚠️ Notification échouée (non bloquant): {}", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> buildCreatedResponse(
            Operation operation, String sequenceId, double amountUSDC, double amountLocal, String currency) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Opération créée automatiquement depuis webhook");
        result.put("operationId", operation.getId());
        result.put("sequenceId", sequenceId);
        result.put("amountUSDC", amountUSDC);
        result.put("amountLocal", amountLocal);
        result.put("currency", currency);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> handleCreationError(Exception e, String sequenceId) {
        log.error("❌ Erreur création opération depuis webhook pour sequenceId={}: {}", sequenceId, e.getMessage(), e);
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("success", false);
        errorBody.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        return ResponseEntity.internalServerError().body(errorBody);
    }

    /**
     * Crée une opération CREDIT PENDING pour un paiement permanent link quand le webhook arrive
     * avant une synchro complète (ex. sessions historiques sans operationHash en base).
     */
    private Operation createPendingCollectionOperationFromPermanentLinkSession(
            PermanentLinkSession session, String sequenceId, String creatorUsername) {
        Operation operation = new Operation();
        operation.setType("CREDIT");
        operation.setStatus("PENDING");
        operation.setUsername(creatorUsername);
        String devise = session.getCurrency() != null && !session.getCurrency().isBlank() ? session.getCurrency() : "XOF";
        operation.setDevise(devise);
        double amt = session.getAmount() != null ? session.getAmount() : 0;
        operation.setAmount(amt);
        operation.setConvertedAmount(amt);
        operation.setDesignation("YELLOWCARD_ON_RAMP");
        operation.setTransactionType("Dépôt");
        operation.setProvider("YellowCard");
        operation.setOperationHash(sequenceId);
        operation.setProviderAmount(0d);
        operation.setProviderDevise("USDC");
        operation.setCreatedAt(LocalDateTime.now());
        operation.setUpdatedAt(LocalDateTime.now());
        return operationRepository.save(operation);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Mapping statut & helpers
    // ═══════════════════════════════════════════════════════════════════════

    private String mapYellowCardStatus(String status, String eventType) {
        if (status != null) {
            return switch (status.toUpperCase()) {
                case "COMPLETED", "SUCCESSFUL" -> "VALIDEE";
                case "FAILED", "CANCELLED", "EXPIRED" -> "ECHOUEE";
                case "REFUNDED" -> "REMBOURSEE";
                case "PENDING", "PROCESSING", "ACTION_REQUIRED", "AWAITING_LINK",
                     "SETTLEMENT_PROCESSING", "FIAT_UPDATED", "UNKNOWN" -> "EN_COURS";
                default -> "EN_COURS";
            };
        }
        if (eventType != null) {
            String evt = eventType.toUpperCase();
            if (evt.contains("COMPLETE") || evt.contains("SUCCESSFUL") || evt.contains("SETTLEMENT_COMPLETE"))
                return "VALIDEE";
            if (evt.contains("FAILED") || evt.contains("CANCELLED") || evt.contains("EXPIRED"))
                return "ECHOUEE";
            if (evt.contains("REFUNDED")) return "REMBOURSEE";
            if (evt.contains("PENDING") || evt.contains("CREATED") || evt.contains("PROCESSING")
                    || evt.contains("ACTION_REQUIRED") || evt.contains("SETTLEMENT")) return "EN_COURS";
        }
        return "EN_COURS";
    }

    private boolean isTerminal(String status) {
        return "VALIDEE".equals(status) || "ECHOUEE".equals(status) || "REMBOURSEE".equals(status);
    }

    private String formatAmount(Operation operation, String type) {
        String label = "PAYMENT".equalsIgnoreCase(type) ? "Retrait confirmé : " : "Paiement reçu : ";
        if (operation.getAmount() != null && operation.getDevise() != null) {
            return label + String.format("%,.0f %s", operation.getAmount(), operation.getDevise());
        }
        return label + "montant inconnu";
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private Double dbl(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }

    private ResponseEntity<Map<String, Object>> ok(boolean success, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", success);
        body.put("message", message);
        return ResponseEntity.ok(body);
    }
}
