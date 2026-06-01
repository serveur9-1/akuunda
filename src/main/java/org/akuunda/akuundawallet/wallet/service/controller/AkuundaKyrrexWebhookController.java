package org.akuunda.akuundawallet.wallet.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.KyrrexTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransaction;
import org.akuunda.akuundawallet.wallet.api.service.WebhookSignatureVerifier;
import org.akuunda.akuundawallet.wallet.service.KyrrexDepositSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/v1/kyrrex")
@Tag(name = "Akuunda Services - Kyrrex Webhooks",
        description = "Endpoint public pour recevoir les webhooks Kyrrex Malta Business")
@RequiredArgsConstructor
public class AkuundaKyrrexWebhookController {

    private final KyrrexTransactionRepository kyrrexTransactionRepository;
    private final OperationRepository operationRepository;
    private final ObjectMapper objectMapper;
    private final WebhookSignatureVerifier webhookSignatureVerifier;
    private final KyrrexDepositSettlementService kyrrexDepositSettlementService;

    @PostMapping("/callback")
    @Operation(
            summary = "Recevoir un webhook Kyrrex",
            description = """
                    Reçoit les événements webhooks envoyés par Kyrrex Malta Business.
                    
                    **Authentification :** la signature HMAC SHA-256 est vérifiée via le header
                    `X-Webhook-Signature`. Le secret est configuré via `kyrrex.webhook.secret`.
                    Le toggle `kyrrex.webhook.signature-verification-enabled` permet de désactiver
                    la vérif (réservé aux environnements de dev).
                    
                    **Types d'événements supportés :**
                    - `deposit` : Dépôt crypto (created → aml_processing → processing → done | frozen | rejected)
                    - `withdraw` : Retrait crypto (initiated → aml_processing → processing → done | rejected | postponed)
                    - `order` : Swap/échange (wait → done | fail | cancel)
                    - `trade` : Trade spot (log uniquement)
                    - `kyc_verified` : Vérification KYC (log uniquement)
                    
                    **Mapping des statuts :**
                    - `created`, `initiated` → INITIATED
                    - `aml_processing`, `processing`, `wait`, `postponed` → PENDING
                    - `done` → SUCCESS (déclenche le règlement automatique vers le wallet Akuunda Pay)
                    - `frozen`, `rejected`, `fail`, `cancel` → FAILED
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KyrrexWebhookPayload.class),
                            examples = {
                                    @ExampleObject(name = "Dépôt complété",
                                            value = """
                                            {"type":"deposit","data":{"uid":"dep-123","status":"done","amount":"0.001","asset":"BTC","txId":"abc123"}}
                                            """),
                                    @ExampleObject(name = "Retrait initié",
                                            value = """
                                            {"type":"withdraw","data":{"uid":"wd-456","status":"initiated","amount":"0.005","asset":"ETH"}}
                                            """),
                                    @ExampleObject(name = "Ordre exécuté",
                                            value = """
                                            {"type":"order","data":{"uid":"ord-789","state":"done","amount":"100","currency":"EUR"}}
                                            """)
                            }
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Webhook traité avec succès",
                            content = @Content(mediaType = "text/plain",
                                    examples = @ExampleObject(value = "OK"))),
                    @ApiResponse(responseCode = "400", description = "Payload invalide"),
                    @ApiResponse(responseCode = "401", description = "Signature webhook invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur interne")
            }
    )
    public ResponseEntity<String> handleKyrrexWebhook(
            @RequestHeader(name = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        try {
            try {
                webhookSignatureVerifier.verify(rawBody, signature);
            } catch (SecurityException se) {
                log.warn("🛡️ Webhook Kyrrex rejeté — signature invalide: {}", se.getMessage());
                return ResponseEntity.status(401).body("Signature invalide");
            }

            KyrrexWebhookPayload payload;
            try {
                payload = objectMapper.readValue(rawBody, KyrrexWebhookPayload.class);
            } catch (Exception parseEx) {
                log.warn("⚠️ Webhook Kyrrex payload invalide: {}", parseEx.getMessage());
                return ResponseEntity.badRequest().body("Payload invalide");
            }

            log.info("📩 Webhook Kyrrex reçu: type={}", payload.getType());

            if (payload.getType() == null) {
                log.warn("⚠️ Webhook Kyrrex sans type");
                return ResponseEntity.badRequest().body("Type manquant");
            }

            switch (payload.getType()) {
                case "deposit" -> handleDeposit(payload);
                case "withdraw" -> handleWithdraw(payload);
                case "order" -> handleOrder(payload);
                case "trade" -> handleTrade(payload);
                case "kyc_verified" -> handleKycVerified(payload);
                default -> log.warn("⚠️ Type de webhook Kyrrex inconnu: {}", payload.getType());
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("❌ Erreur traitement webhook Kyrrex: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Erreur interne");
        }
    }

    private void handleDeposit(KyrrexWebhookPayload payload) {
        try {
            KyrrexWebhookDepositData data = objectMapper.treeToValue(payload.getData(), KyrrexWebhookDepositData.class);
            log.info("💰 Webhook dépôt Kyrrex: uid={}, status={}, asset={}, amount={}",
                    data.getUid(), data.getStatus(), data.getAsset(), data.getAmount());

            String normalizedStatus = mapStatus(data.getStatus());

            Optional<KyrrexTransaction> txOpt = kyrrexTransactionRepository.findByKyrrexId(data.getUid());
            txOpt.ifPresentOrElse(tx -> {
                tx.setStatus(normalizedStatus);
                tx.setUpdatedAt(Instant.now());
                kyrrexTransactionRepository.save(tx);
                log.info("✅ KyrrexTransaction mis à jour: uid={}, status={}", data.getUid(), normalizedStatus);
            }, () -> log.warn("⚠️ KyrrexTransaction non trouvée pour uid={}", data.getUid()));

            updateOperation(data.getUid(), normalizedStatus);

            if ("SUCCESS".equals(normalizedStatus)) {
                triggerSettlement(data.getUid(), "deposit");
            }

        } catch (Exception e) {
            log.error("❌ Erreur traitement webhook dépôt: {}", e.getMessage(), e);
        }
    }

    private void handleWithdraw(KyrrexWebhookPayload payload) {
        try {
            KyrrexWebhookDepositData data = objectMapper.treeToValue(payload.getData(), KyrrexWebhookDepositData.class);
            log.info("💸 Webhook retrait Kyrrex: uid={}, status={}, asset={}, amount={}",
                    data.getUid(), data.getStatus(), data.getAsset(), data.getAmount());

            String normalizedStatus = mapStatus(data.getStatus());

            Optional<KyrrexTransaction> txOpt = kyrrexTransactionRepository.findByKyrrexId(data.getUid());
            txOpt.ifPresentOrElse(tx -> {
                tx.setStatus(normalizedStatus);
                tx.setUpdatedAt(Instant.now());
                kyrrexTransactionRepository.save(tx);
                log.info("✅ KyrrexTransaction retrait mis à jour: uid={}, status={}", data.getUid(), normalizedStatus);
            }, () -> log.warn("⚠️ KyrrexTransaction retrait non trouvée pour uid={}", data.getUid()));

            updateOperation(data.getUid(), normalizedStatus);

            // Si ce retrait correspond au payout on-chain initié après dépôt,
            // on met à jour la transaction de dépôt source.
            kyrrexTransactionRepository.findByPayoutWithdrawalId(data.getUid()).ifPresent(parentDepositTx -> {
                if ("SUCCESS".equals(normalizedStatus)) {
                    parentDepositTx.setPayoutStatus("SUCCESS");
                    parentDepositTx.setPayoutConfirmedAt(Instant.now());
                } else if ("FAILED".equals(normalizedStatus)) {
                    parentDepositTx.setPayoutStatus("FAILED");
                } else {
                    parentDepositTx.setPayoutStatus("PENDING");
                }
                parentDepositTx.setUpdatedAt(Instant.now());
                kyrrexTransactionRepository.save(parentDepositTx);
            });

        } catch (Exception e) {
            log.error("❌ Erreur traitement webhook retrait: {}", e.getMessage(), e);
        }
    }

    private void handleOrder(KyrrexWebhookPayload payload) {
        try {
            KyrrexWebhookOrderData data = objectMapper.treeToValue(payload.getData(), KyrrexWebhookOrderData.class);
            log.info("🔄 Webhook ordre Kyrrex: uid={}, state={}, amount={}, currency={}",
                    data.getUid(), data.getState(), data.getAmount(), data.getCurrency());

            String normalizedStatus = mapStatus(data.getState());

            Optional<KyrrexTransaction> txOpt = kyrrexTransactionRepository.findByKyrrexId(data.getUid());
            txOpt.ifPresentOrElse(tx -> {
                tx.setStatus(normalizedStatus);
                tx.setUpdatedAt(Instant.now());
                kyrrexTransactionRepository.save(tx);
                log.info("✅ KyrrexTransaction ordre mis à jour: uid={}, status={}", data.getUid(), normalizedStatus);
            }, () -> log.warn("⚠️ KyrrexTransaction ordre non trouvée pour uid={}", data.getUid()));

            updateOperation(data.getUid(), normalizedStatus);

            // Un ordre Kyrrex en succès correspond souvent à la finalisation d'un
            // dépôt fiat -> crypto (advanced exchange). On déclenche le règlement
            // si la transaction est éligible (type DEPOSIT/ON_RAMP/ADVANCED_EXCHANGE).
            if ("SUCCESS".equals(normalizedStatus)) {
                triggerSettlement(data.getUid(), "order");
            }

        } catch (Exception e) {
            log.error("❌ Erreur traitement webhook ordre: {}", e.getMessage(), e);
        }
    }

    private void handleTrade(KyrrexWebhookPayload payload) {
        try {
            KyrrexWebhookTradeData data = objectMapper.treeToValue(payload.getData(), KyrrexWebhookTradeData.class);
            log.info("📊 Webhook trade Kyrrex (log uniquement): id={}, market={}, side={}, price={}, volume={}",
                    data.getId(), data.getMarket(), data.getSide(), data.getPrice(), data.getVolume());
        } catch (Exception e) {
            log.error("❌ Erreur traitement webhook trade: {}", e.getMessage(), e);
        }
    }

    private void handleKycVerified(KyrrexWebhookPayload payload) {
        try {
            KyrrexWebhookKycData data = objectMapper.treeToValue(payload.getData(), KyrrexWebhookKycData.class);
            if (data.getMember() != null) {
                log.info("✅ Webhook KYC Kyrrex: membre uid={}, email={}, verified={}",
                        data.getMember().getUid(), data.getMember().getEmail(), data.getMember().isVerified());
            } else {
                log.info("✅ Webhook KYC Kyrrex reçu (données membre indisponibles)");
            }
        } catch (Exception e) {
            log.error("❌ Erreur traitement webhook KYC: {}", e.getMessage(), e);
        }
    }

    private void updateOperation(String hash, String normalizedStatus) {
        if (hash == null) return;
        try {
            org.akuunda.akuundawallet.wallet.api.entities.Operation op = operationRepository.findByOperationHash(hash);
            if (op != null) {
                op.setStatus(normalizedStatus);
                op.setUpdatedAt(LocalDateTime.now());
                operationRepository.saveAndFlush(op);
                log.info("✅ Operation mis à jour: hash={}, status={}", hash, normalizedStatus);
            } else {
                log.warn("⚠️ Operation non trouvée pour hash={}", hash);
            }
        } catch (Exception e) {
            log.error("❌ Erreur mise à jour operation: {}", e.getMessage(), e);
        }
    }

    /**
     * Déclenche le règlement automatique (crédit ledger Akuunda Pay) pour un
     * dépôt Kyrrex confirmé. L'idempotence est garantie par le service.
     */
    private void triggerSettlement(String kyrrexId, String origin) {
        try {
            boolean settled = kyrrexDepositSettlementService.settleIfEligibleByKyrrexId(kyrrexId);
            if (settled) {
                log.info("✅ Règlement Kyrrex déclenché depuis webhook {} pour uid={}", origin, kyrrexId);
            }
        } catch (Exception e) {
            log.error("❌ Erreur règlement automatique Kyrrex (origin={}, uid={}): {}",
                    origin, kyrrexId, e.getMessage(), e);
        }
    }

    private String mapStatus(String kyrrexStatus) {
        if (kyrrexStatus == null) return "unknown";
        return switch (kyrrexStatus.toLowerCase()) {
            case "created", "initiated" -> "INITIATED";
            case "aml_processing", "processing", "wait", "postponed" -> "PENDING";
            case "done" -> "SUCCESS";
            case "frozen", "rejected", "fail", "cancel" -> "FAILED";
            default -> kyrrexStatus;
        };
    }
}
