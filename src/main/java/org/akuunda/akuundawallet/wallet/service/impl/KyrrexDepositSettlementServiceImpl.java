package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexCryptoAddressValidationRequest;
import org.akuunda.akuundawallet.wallet.api.dao.KyrrexTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransactionType;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.KyrrexDepositSettlementService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaKyrrexClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Voir {@link KyrrexDepositSettlementService} pour le contrat.
 *
 * <p>Stratégie de calcul du montant USDC crédité (ledger) :
 * <ol>
 *   <li>Si l'asset crypto déposé est déjà l'asset cible (USDC) -> 1:1.</li>
 *   <li>Si dépôt fiat -> conversion via CurrencyFreaks (fiat -> USD ≈ USDC).</li>
 *   <li>Sinon (autre crypto, ex: BTC, ETH) -> on logue un warning et on règle
 *       le ledger avec le montant brut ; le treasury back-office se chargera
 *       du swap final via Kyrrex (option {@code auto-fiat-conversion-enabled}
 *       indépendante).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KyrrexDepositSettlementServiceImpl implements KyrrexDepositSettlementService {

    private static final Set<String> TERMINAL_SUCCESS_STATUSES = Set.of("SUCCESS", "VALIDEE", "done");

    private static final Set<KyrrexTransactionType> DEPOSIT_TYPES = Set.of(
            KyrrexTransactionType.DEPOSIT,
            KyrrexTransactionType.ON_RAMP,
            KyrrexTransactionType.ADVANCED_EXCHANGE
    );

    private final KyrrexTransactionRepository kyrrexTransactionRepository;
    private final OperationRepository operationRepository;
    private final WalletRepository walletRepository;
    private final CurrencyFreaksClientService currencyFreaksClientService;
    private final TransactionNotificationHelper notificationHelper;
    private final AkuundaKyrrexClientService kyrrexClientService;
    private final ObjectMapper objectMapper;

    @Value("${akuunda.kyrrex.settlement.enabled:true}")
    private boolean settlementEnabled;

    @Value("${akuunda.kyrrex.settlement.target-asset:USDC}")
    private String targetAsset;

    @Value("${akuunda.kyrrex.settlement.target-network:Polygon}")
    private String targetNetwork;

    @Value("${akuunda.kyrrex.settlement.onchain-payout-enabled:true}")
    private boolean onchainPayoutEnabled;

    @Override
    public boolean settleIfEligibleByKyrrexId(String kyrrexId) {
        if (kyrrexId == null || kyrrexId.isBlank()) return false;
        return kyrrexTransactionRepository.findByKyrrexId(kyrrexId)
                .map(this::settleIfEligible)
                .orElseGet(() -> {
                    log.warn("⚠️ [KYRREX-SETTLE] Aucun KyrrexTransaction trouvé pour uid={}", kyrrexId);
                    return false;
                });
    }

    @Override
    @Transactional
    public boolean settleIfEligible(KyrrexTransaction transaction) {
        if (!settlementEnabled) {
            log.debug("⏸️ [KYRREX-SETTLE] Désactivé (akuunda.kyrrex.settlement.enabled=false)");
            return false;
        }
        if (transaction == null) {
            return false;
        }

        if (!isDepositType(transaction.getType())) {
            log.debug("[KYRREX-SETTLE] Skip — type non éligible: {} (uid={})",
                    transaction.getType(), transaction.getKyrrexId());
            return false;
        }

        if (!isTerminalSuccess(transaction.getStatus())) {
            log.debug("[KYRREX-SETTLE] Skip — statut non terminal succès: {} (uid={})",
                    transaction.getStatus(), transaction.getKyrrexId());
            return false;
        }

        if (transaction.getSettledAt() != null) {
            log.info("ℹ️ [KYRREX-SETTLE] Déjà réglé le {} — uid={}, montant={} {}",
                    transaction.getSettledAt(), transaction.getKyrrexId(),
                    transaction.getSettlementAmount(), transaction.getSettlementAsset());
            return false;
        }

        String username = transaction.getUsername();
        if (username == null || username.isBlank()) {
            log.warn("⚠️ [KYRREX-SETTLE] username manquant sur KyrrexTransaction id={}", transaction.getId());
            return false;
        }

        Wallet wallet = walletRepository.findByUsersUsername(username);
        if (wallet == null) {
            log.warn("⚠️ [KYRREX-SETTLE] Wallet introuvable pour username={} (uid={})",
                    username, transaction.getKyrrexId());
            return false;
        }

        BigDecimal usdcAmount = computeUsdcEquivalent(transaction);
        if (usdcAmount == null || usdcAmount.signum() <= 0) {
            log.warn("⚠️ [KYRREX-SETTLE] Montant USDC indéterminé/nul pour uid={}, asset={}, amount={}",
                    transaction.getKyrrexId(), transaction.getAsset(), transaction.getAmount());
            return false;
        }

        String localCurrency = resolveLocalCurrency(wallet);
        double localAmount = convertUsdToLocal(usdcAmount.doubleValue(), localCurrency);

        double balanceBefore = wallet.getBalance();
        double deviseBefore = wallet.getDeviseBalance();
        wallet.setBalance(balanceBefore + usdcAmount.doubleValue());
        wallet.setDeviseBalance(deviseBefore + localAmount);
        walletRepository.saveAndFlush(wallet);

        log.info("✅ [KYRREX-SETTLE] Wallet crédité — user={}, +{} USDC ({} -> {}), +{} {} ({} -> {}) — uid={}",
                username, usdcAmount, balanceBefore, wallet.getBalance(),
                String.format("%.2f", localAmount), localCurrency,
                String.format("%.2f", deviseBefore), String.format("%.2f", wallet.getDeviseBalance()),
                transaction.getKyrrexId());

        upsertOperation(transaction, username, localCurrency, localAmount, usdcAmount);

        transaction.setSettledAt(Instant.now());
        transaction.setSettlementAmount(usdcAmount);
        transaction.setSettlementAsset(targetAsset);
        transaction.setSettlementNetwork(targetNetwork);
        kyrrexTransactionRepository.save(transaction);

        if (onchainPayoutEnabled) {
            triggerOnchainPayout(transaction, wallet, usdcAmount);
        } else {
            transaction.setPayoutStatus("SKIPPED_DISABLED");
            kyrrexTransactionRepository.save(transaction);
        }

        notifySafe(username, localAmount, localCurrency, transaction.getKyrrexId());

        return true;
    }

    // ────────────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────────────

    private boolean isDepositType(KyrrexTransactionType type) {
        return type != null && DEPOSIT_TYPES.contains(type);
    }

    private boolean isTerminalSuccess(String status) {
        if (status == null) return false;
        String upper = status.trim();
        return TERMINAL_SUCCESS_STATUSES.stream().anyMatch(upper::equalsIgnoreCase);
    }

    /**
     * Détermine le montant USDC à créditer côté ledger Akuunda Pay.
     * <ul>
     *   <li>Asset crypto déjà USDC -> {@code amount}.</li>
     *   <li>Asset crypto stablecoin USD-pegged (USDT, DAI...) -> 1:1 USD.</li>
     *   <li>Dépôt fiat -> conversion CurrencyFreaks fiat -> USD.</li>
     *   <li>Sinon -> on règle avec le montant brut, le treasury swap-out
     *       est traité en back-office.</li>
     * </ul>
     */
    private BigDecimal computeUsdcEquivalent(KyrrexTransaction tx) {
        BigDecimal cryptoAmount = tx.getCryptoAmount() != null ? tx.getCryptoAmount() : tx.getAmount();
        String cryptoAsset = firstNonBlank(tx.getCryptoAsset(), tx.getAsset());

        if (cryptoAmount != null && cryptoAmount.signum() > 0 && isUsdPegged(cryptoAsset)) {
            return cryptoAmount.setScale(8, RoundingMode.HALF_UP);
        }

        if (tx.getFiatAmount() != null && tx.getFiatAmount().signum() > 0
                && tx.getFiatCurrency() != null && !tx.getFiatCurrency().isBlank()) {
            double usd = convertToUsd(tx.getFiatAmount().doubleValue(), tx.getFiatCurrency());
            if (usd > 0) {
                return BigDecimal.valueOf(usd).setScale(8, RoundingMode.HALF_UP);
            }
        }

        if (cryptoAmount != null && cryptoAmount.signum() > 0) {
            log.warn("⚠️ [KYRREX-SETTLE] Asset {} non USD-pegged — règlement au montant brut, treasury sweep requis (uid={})",
                    cryptoAsset, tx.getKyrrexId());
            return cryptoAmount.setScale(8, RoundingMode.HALF_UP);
        }

        return null;
    }

    private boolean isUsdPegged(String asset) {
        if (asset == null) return false;
        String upper = asset.trim().toUpperCase();
        return upper.equals("USDC") || upper.equals("USDT") || upper.equals("DAI")
                || upper.equals("BUSD") || upper.equals("USD");
    }

    private double convertToUsd(double amount, String fromCurrency) {
        if ("USD".equalsIgnoreCase(fromCurrency)) return amount;
        try {
            var resp = currencyFreaksClientService.convertCurrency(fromCurrency, "USD", amount);
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                String converted = resp.getBody().getConvertedAmount();
                if (converted != null && !converted.isBlank()) {
                    return Double.parseDouble(converted);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [KYRREX-SETTLE] Conversion {} -> USD échouée: {}", fromCurrency, e.getMessage());
        }
        return 0d;
    }

    private double convertUsdToLocal(double usd, String localCurrency) {
        if (localCurrency == null || "USD".equalsIgnoreCase(localCurrency) || "USDC".equalsIgnoreCase(localCurrency)) {
            return usd;
        }
        try {
            var resp = currencyFreaksClientService.convertCurrency("USD", localCurrency, usd);
            if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                String converted = resp.getBody().getConvertedAmount();
                if (converted != null && !converted.isBlank()) {
                    return Double.parseDouble(converted);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [KYRREX-SETTLE] Conversion USD -> {} échouée: {}", localCurrency, e.getMessage());
        }
        return usd;
    }

    private String resolveLocalCurrency(Wallet wallet) {
        try {
            Users user = wallet.getUsers();
            if (user != null && user.getCountryCurrency() != null
                    && user.getCountryCurrency().getCurrencyCode() != null) {
                return user.getCountryCurrency().getCurrencyCode();
            }
        } catch (Exception e) {
            log.warn("⚠️ [KYRREX-SETTLE] Erreur résolution devise locale: {}", e.getMessage());
        }
        return "USD";
    }

    private void upsertOperation(KyrrexTransaction tx, String username,
                                 String localCurrency, double localAmount, BigDecimal usdcAmount) {
        try {
            String hash = tx.getKyrrexId();
            Operation op = hash != null ? operationRepository.findByOperationHash(hash) : null;
            if (op == null) {
                op = new Operation();
                op.setOperationHash(hash != null ? hash : ("kyrrex-" + tx.getId()));
                op.setUsername(username);
                op.setType("CREDIT");
                op.setDesignation("KYRREX_ON_RAMP");
                op.setTransactionType("Dépôt");
                op.setProvider("Kyrrex");
                op.setCreatedAt(LocalDateTime.now());
            }
            op.setStatus("VALIDEE");
            op.setDevise(localCurrency);
            op.setAmount(localAmount);
            op.setConvertedAmount(localAmount);
            op.setProviderAmount(usdcAmount.doubleValue());
            op.setProviderDevise(targetAsset);
            op.setUpdatedAt(LocalDateTime.now());
            operationRepository.saveAndFlush(op);
        } catch (Exception e) {
            log.error("❌ [KYRREX-SETTLE] Erreur mise à jour Operation pour uid={}: {}",
                    tx.getKyrrexId(), e.getMessage(), e);
        }
    }

    private void notifySafe(String username, double localAmount, String currency, String reference) {
        try {
            String formatted = String.format("%,.2f %s", localAmount, currency);
            notificationHelper.notifyTransactionSuccess(username, formatted, reference);
        } catch (Exception e) {
            log.warn("⚠️ [KYRREX-SETTLE] Notification non critique échouée pour {}: {}", username, e.getMessage());
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b;
    }

    private void triggerOnchainPayout(KyrrexTransaction tx, Wallet wallet, BigDecimal amount) {
        if (tx.getPayoutConfirmedAt() != null || "SUCCESS".equalsIgnoreCase(tx.getPayoutStatus())) {
            return;
        }
        if (tx.getPayoutWithdrawalId() != null && !tx.getPayoutWithdrawalId().isBlank()) {
            tx.setPayoutStatus("WITHDRAWAL_ALREADY_INITIATED");
            kyrrexTransactionRepository.save(tx);
            return;
        }

        String address = wallet.getAddress();
        if (address == null || address.isBlank()) {
            tx.setPayoutStatus("FAILED_MISSING_WALLET_ADDRESS");
            kyrrexTransactionRepository.save(tx);
            return;
        }

        String username = tx.getUsername();
        try {
            ResponseEntity<?> validation = kyrrexClientService.validateCryptoWithdrawalAddress(
                    username,
                    new KyrrexCryptoAddressValidationRequest(address, targetNetwork, targetNetwork)
            );
            if (validation == null || !validation.getStatusCode().is2xxSuccessful()) {
                tx.setPayoutStatus("FAILED_ADDRESS_VALIDATION");
                kyrrexTransactionRepository.save(tx);
                return;
            }

            String requisiteId = resolveOrCreateRequisite(username, address);
            if (requisiteId == null || requisiteId.isBlank()) {
                tx.setPayoutStatus("FAILED_REQUISITE");
                kyrrexTransactionRepository.save(tx);
                return;
            }

            ResponseEntity<String> withdrawal = kyrrexClientService.executeCryptoWithdrawal(
                    username, targetAsset, amount, requisiteId);
            if (withdrawal == null || !withdrawal.getStatusCode().is2xxSuccessful()) {
                tx.setPayoutStatus("FAILED_WITHDRAWAL_REQUEST");
                kyrrexTransactionRepository.save(tx);
                return;
            }

            String withdrawalUid = extractField(withdrawal.getBody(), "uid", "id");
            tx.setPayoutWithdrawalId(withdrawalUid);
            tx.setPayoutStartedAt(Instant.now());
            tx.setPayoutStatus("WITHDRAWAL_PENDING");
            kyrrexTransactionRepository.save(tx);
        } catch (Exception e) {
            log.error("❌ [KYRREX-SETTLE] Erreur payout on-chain uid={}: {}", tx.getKyrrexId(), e.getMessage(), e);
            tx.setPayoutStatus("FAILED_EXCEPTION");
            kyrrexTransactionRepository.save(tx);
        }
    }

    private String resolveOrCreateRequisite(String username, String walletAddress) {
        try {
            ResponseEntity<String> list = kyrrexClientService.getRequisites(username);
            String found = extractRequisiteIdForAddress(list != null ? list.getBody() : null, walletAddress);
            if (found != null) return found;

            ResponseEntity<String> created = kyrrexClientService.createRequisite(
                    username, targetAsset.toLowerCase(Locale.ROOT), walletAddress, targetNetwork, "Akuunda wallet"
            );
            String createdId = extractField(created != null ? created.getBody() : null, "uid", "id", "requisite_id");
            if (createdId != null) return createdId;

            ResponseEntity<String> refresh = kyrrexClientService.getRequisites(username);
            return extractRequisiteIdForAddress(refresh != null ? refresh.getBody() : null, walletAddress);
        } catch (Exception e) {
            log.warn("⚠️ [KYRREX-SETTLE] resolveOrCreateRequisite échoué: {}", e.getMessage());
            return null;
        }
    }

    private String extractRequisiteIdForAddress(String rawJson, String walletAddress) {
        if (rawJson == null || rawJson.isBlank() || walletAddress == null) return null;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    if (walletAddress.equalsIgnoreCase(item.path("address").asText())) {
                        String id = field(item, "uid", "id", "requisite_id");
                        if (id != null) return id;
                    }
                }
            } else if (root.has("items") && root.get("items").isArray()) {
                for (JsonNode item : root.get("items")) {
                    if (walletAddress.equalsIgnoreCase(item.path("address").asText())) {
                        String id = field(item, "uid", "id", "requisite_id");
                        if (id != null) return id;
                    }
                }
            }
        } catch (Exception ignored) {
            // no-op
        }
        return null;
    }

    private String extractField(String rawJson, String... keys) {
        if (rawJson == null || rawJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root.isArray() && root.size() > 0) {
                return field(root.get(0), keys);
            }
            return field(root, keys);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String field(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        JsonNode data = node.get("data");
        if (data != null && !data.isNull()) {
            for (String key : keys) {
                JsonNode v = data.get(key);
                if (v != null && !v.isNull() && !v.asText().isBlank()) {
                    return v.asText();
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Optional<KyrrexTransaction> reload(KyrrexTransaction tx) {
        return tx != null && tx.getId() != null
                ? kyrrexTransactionRepository.findById(tx.getId())
                : Optional.empty();
    }
}
