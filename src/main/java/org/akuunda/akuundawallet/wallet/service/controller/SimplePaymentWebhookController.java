package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;  // ← NOUVEAU
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = SimplePaymentWebhookController.API_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - YellowCard Simple Payment Webhook",
        description = "Callback de synchronisation pour les paiements simples via YellowCard (generelinkYC)")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class SimplePaymentWebhookController {

    public static final String API_ROOT = "/api/internal/v1/yellowcard-simple-webhook";

    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final OperationRepository operationRepository;
    private final WalletRepository walletRepository;
    private final CurrencyFreaksClientService currencyFreaksClientService;
    private final TransactionNotificationHelper notificationHelper;  // ← NOUVEAU

    @PostMapping("/payment-completed")
    @io.swagger.v3.oas.annotations.Operation(
            operationId = "yellowCardSimplePaymentCompleted",
            summary = "Callback paiement simple YellowCard complété",
            description = "Appelé par akuundapay_generelinkYC après COLLECTION.COMPLETED. "
                    + "Met à jour le lien, crédite le wallet marchand, crée l'opération historique, notifie le marchand."
    )
    public ResponseEntity<Map<String, Object>> paymentCompleted(@RequestBody Map<String, Object> payload) {
        log.info("🔵 Callback paiement simple YellowCard reçu: {}", payload);

        try {
            String sequenceId      = getStringOrNull(payload, "sequenceId");
            String uniqueCode      = getStringOrNull(payload, "uniqueCode");
            String status          = getStringOrNull(payload, "status");
            Double localAmount     = getDoubleOrNull(payload, "localAmount");
            String localCurrency   = getStringOrNull(payload, "localCurrency");
            Double settledAmount   = getDoubleOrNull(payload, "settledAmount");
            String settledCurrency = getStringOrNull(payload, "settledCurrency");
            String payerPhone      = getStringOrNull(payload, "payerPhone");
            String payerName       = getStringOrNull(payload, "payerName");
            String payerEmail      = getStringOrNull(payload, "payerEmail");
            String networkName     = getStringOrNull(payload, "networkName");
            String yellowCardTxId  = getStringOrNull(payload, "yellowCardTxId");

            if (!"COMPLETED".equalsIgnoreCase(status)) {
                log.info("ℹ️ Statut non-COMPLETED reçu ({}), ignoré.", status);
                return ResponseEntity.ok(Map.of("success", true, "message", "Statut non-COMPLETED ignoré"));
            }

            // ── Retrouver le OneTimePaymentLink ──
            OneTimePaymentLink link = null;

            if (sequenceId != null && !sequenceId.isEmpty()) {
                Optional<OneTimePaymentLink> opt = oneTimePaymentLinkRepository.findByYcSequenceId(sequenceId);
                if (opt.isPresent()) {
                    link = opt.get();
                    log.info("✅ Lien trouvé par ycSequenceId={}: uniqueCode={}", sequenceId, link.getUniqueCode());
                }
            }

            if (link == null && uniqueCode != null && !uniqueCode.isEmpty()) {
                Optional<OneTimePaymentLink> opt = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode);
                if (opt.isPresent()) {
                    link = opt.get();
                    log.info("✅ Lien trouvé par uniqueCode={}", uniqueCode);
                }
            }

            if (link == null) {
                log.warn("⚠️ Aucun OneTimePaymentLink trouvé pour sequenceId={}, uniqueCode={}.", sequenceId, uniqueCode);
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Lien de paiement non trouvé",
                        "sequenceId", sequenceId != null ? sequenceId : "",
                        "uniqueCode", uniqueCode != null ? uniqueCode : ""
                ));
            }

            // ── Idempotence ──
            if ("PAID".equals(link.getStatus())) {
                log.info("ℹ️ Lien déjà en statut PAID, doublon ignoré: uniqueCode={}", link.getUniqueCode());
                return ResponseEntity.ok(Map.of("success", true, "message", "Déjà traité (PAID)"));
            }

            // ── Récupérer le créateur (marchand) et son wallet ──
            Users creator = link.getCreator();
            if (creator == null) {
                log.error("❌ Créateur du lien non trouvé: uniqueCode={}", link.getUniqueCode());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "error", "Créateur du lien non trouvé"));
            }

            Wallet creatorWallet = walletRepository.findByUsers(creator);
            if (creatorWallet == null) {
                log.error("❌ Wallet du créateur non trouvé: username={}", creator.getUsername());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("success", false, "error", "Wallet du créateur non trouvé"));
            }

            // ── Calculer montant USDC ──
            Double amountUSDC = null;

            if (settledAmount != null && settledAmount > 0) {
                amountUSDC = settledAmount;
                log.info("✅ settledAmount reçu de YC: {} {}", amountUSDC,
                        settledCurrency != null ? settledCurrency : "USDC");
            }

            if (amountUSDC == null && localAmount != null && localAmount > 0 && localCurrency != null) {
                try {
                    var conv = currencyFreaksClientService.convertCurrency(localCurrency, "USD", localAmount);
                    if (conv != null && conv.getStatusCode().is2xxSuccessful() && conv.getBody() != null) {
                        String convertedStr = conv.getBody().getConvertedAmount();
                        if (convertedStr != null && !convertedStr.isEmpty()) {
                            amountUSDC = Double.parseDouble(convertedStr);
                            log.info("✅ Conversion fallback: {} {} → {} USDC", localAmount, localCurrency, amountUSDC);
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Erreur conversion {} {} → USD: {}", localAmount, localCurrency, e.getMessage());
                }
            }

            if (amountUSDC == null || amountUSDC <= 0) {
                if (link.getSourceAmount() != null && link.getSourceAmount() > 0) {
                    amountUSDC = link.getSourceAmount();
                    log.info("⚠️ Fallback vers sourceAmount du lien: {} USDC", amountUSDC);
                } else {
                    amountUSDC = 0.0;
                    log.warn("⚠️ Aucun montant USDC déterminable, utilisation de 0.0");
                }
            }

            // ── Montant en devise locale du MARCHAND ──
            String merchantCurrency = creatorWallet.getCurrency() != null
                    ? creatorWallet.getCurrency().getCurrencyCode()
                    : null;

            Double amountInMerchantCurrency;

            if (localAmount != null && localAmount > 0
                    && localCurrency != null
                    && merchantCurrency != null
                    && localCurrency.equalsIgnoreCase(merchantCurrency)) {
                amountInMerchantCurrency = localAmount;
                log.info("✅ Devise payeur = devise marchand ({}), utilisation directe: {}",
                        merchantCurrency, localAmount);
            } else if (merchantCurrency != null && !merchantCurrency.equalsIgnoreCase("USDC")) {
                amountInMerchantCurrency = amountUSDC;
                try {
                    var conv = currencyFreaksClientService.convertCurrency("USD", merchantCurrency, amountUSDC);
                    if (conv != null && conv.getStatusCode().is2xxSuccessful() && conv.getBody() != null) {
                        String convertedStr = conv.getBody().getConvertedAmount();
                        if (convertedStr != null && !convertedStr.isEmpty()) {
                            amountInMerchantCurrency = Double.parseDouble(convertedStr);
                            log.info("✅ Conversion USDC → {}: {} → {}",
                                    merchantCurrency, amountUSDC, amountInMerchantCurrency);
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Erreur conversion USDC → {}: {}. Fallback USDC.", merchantCurrency, e.getMessage());
                }
            } else {
                amountInMerchantCurrency = amountUSDC;
                merchantCurrency = "USDC";
            }

            // ── Créditer le wallet ──
            double balanceBefore = creatorWallet.getBalance();
            double deviseBalanceBefore = creatorWallet.getDeviseBalance();

            creatorWallet.setBalance(balanceBefore + amountUSDC);
            creatorWallet.setDeviseBalance(deviseBalanceBefore + amountInMerchantCurrency);
            walletRepository.save(creatorWallet);

            log.info("✅ Wallet crédité: username={}, balance: {} → {} USDC, deviseBalance: {} → {} {}",
                    creator.getUsername(),
                    balanceBefore, creatorWallet.getBalance(),
                    deviseBalanceBefore, creatorWallet.getDeviseBalance(),
                    merchantCurrency);

            // ── Créer l'opération CREDIT ──
            String payerDisplayName = payerName != null && !payerName.isEmpty()
                    ? payerName
                    : (payerPhone != null && !payerPhone.isEmpty() ? payerPhone : "Payeur externe");

            Operation operation = new Operation();
            operation.setType("CREDIT");
            operation.setStatus("VALIDEE");
            operation.setUsername(creator.getUsername());
            operation.setDevise(merchantCurrency);
            operation.setAmount(amountInMerchantCurrency);
            operation.setConvertedAmount(amountInMerchantCurrency);
            operation.setDesignation("ONE_TIME_PAYMENT_RECEIVED");
            operation.setTransactionType("Encaissement");
            operation.setProvider("YellowCard");
            operation.setProviderAmount(amountUSDC);
            operation.setProviderDevise(settledCurrency != null ? settledCurrency : "USDC");
            operation.setCounterpartUsername(payerPhone);
            operation.setCounterpartDisplayName(payerDisplayName);
            operation.setWalletBalanceBefore(balanceBefore);
            operation.setWalletBalanceAfter(creatorWallet.getBalance());
            operation.setWalletDeviseBalanceBefore(deviseBalanceBefore);
            operation.setWalletDeviseBalanceAfter(creatorWallet.getDeviseBalance());
            operation.setOperationHash("YC-SIMPLE-" + System.currentTimeMillis() + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            operation.setCreatedAt(LocalDateTime.now());
            operation.setUpdatedAt(LocalDateTime.now());

            operation = operationRepository.save(operation);
            log.info("✅ Opération CREDIT créée: id={}, hash={}, {} {} (provider: {} {})",
                    operation.getId(), operation.getOperationHash(),
                    operation.getAmount(), operation.getDevise(),
                    operation.getProviderAmount(), operation.getProviderDevise());

            // ── Mettre à jour le OneTimePaymentLink ──
            String oldStatus = link.getStatus();
            link.setStatus("PAID");
            link.setPaidAt(LocalDateTime.now());
            link.setUpdatedAt(LocalDateTime.now());

            if (payerPhone != null && !payerPhone.isEmpty()) link.setPayerPhone(payerPhone);
            if (payerName != null && !payerName.isEmpty()) link.setPayerName(payerName);
            if (payerEmail != null && !payerEmail.isEmpty()) link.setPayerEmail(payerEmail);
            if (yellowCardTxId != null && !yellowCardTxId.isEmpty()) link.setYellowCardTransactionId(yellowCardTxId);
            if (sequenceId != null && !sequenceId.isEmpty()
                    && (link.getYcSequenceId() == null || link.getYcSequenceId().isEmpty())) {
                link.setYcSequenceId(sequenceId);
            }

            if (amountUSDC > 0) {
                link.setSourceAmount(amountUSDC);
                link.setSourceCurrency(settledCurrency != null ? settledCurrency : "USDC");
            }

            if (localAmount != null && localAmount > 0 && localCurrency != null) {
                link.setAmount(localAmount);
                link.setCurrency(localCurrency);
            }

            oneTimePaymentLinkRepository.save(link);
            log.info("✅ Lien mis à jour: uniqueCode={}, {} → PAID, payer={}, network={}",
                    link.getUniqueCode(), oldStatus, payerDisplayName, networkName);

            // ── Notification push au marchand ── ← NOUVEAU
            try {
                String amountDisplay = String.format("%,.0f %s", amountInMerchantCurrency, merchantCurrency);
                notificationHelper.notifyTransactionSuccess(
                        creator.getUsername(),
                        "Paiement reçu : " + amountDisplay + " de " + payerDisplayName,
                        operation.getOperationHash());
                log.info("🔔 Notification push envoyée au marchand {} pour {}",
                        creator.getUsername(), operation.getOperationHash());
            } catch (Exception e) {
                log.warn("⚠️ Notification push marchand échouée (non bloquant): {}", e.getMessage());
            }

            // ── Réponse ──
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Paiement synchronisé avec succès",
                    "uniqueCode", link.getUniqueCode(),
                    "operationId", operation.getId(),
                    "amountCreditedUSDC", amountUSDC,
                    "amountCreditedLocal", amountInMerchantCurrency,
                    "merchantCurrency", merchantCurrency,
                    "merchantUsername", creator.getUsername()
            ));

        } catch (Exception e) {
            log.error("❌ Erreur lors du traitement du callback paiement simple YellowCard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
                    ));
        }
    }

    private String getStringOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private Double getDoubleOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
