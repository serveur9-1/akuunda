package org.akuunda.akuundawallet.wallet.service.task;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Surveille le solde USDC on-chain des wallets utilisateurs par batch.
 * 
 * <p>Optimisations performance :
 * <ul>
 *   <li>Traitement par batch (ex: 20 wallets par cycle) au lieu de tous à chaque fois</li>
 *   <li>Round-robin : tous les wallets sont couverts sur plusieurs cycles</li>
 *   <li>Cache mémoire pour éviter les requêtes BDD inutiles</li>
 *   <li>Seuil configurable pour ignorer les micro-variations</li>
 * </ul>
 * 
 * <p>Avec 1000 wallets, batch=20, interval=30s → chaque wallet est vérifié toutes les 25 min.
 * Avec 100 wallets, batch=20, interval=30s → chaque wallet est vérifié toutes les 2.5 min.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletBalanceMonitorTask {

    private final WalletRepository walletRepository;
    private final PaymentFactoryContractService paymentFactoryContractService;
    private final TransactionNotificationHelper notificationHelper;

    private final Map<String, Double> lastKnownBalances = new ConcurrentHashMap<>();

    /**
     * Index round-robin pour savoir où on en est dans la liste des wallets.
     */
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    /**
     * Nombre de wallets traités par cycle de polling.
     */
    @Value("${akuunda.wallet.monitor.batch-size:20}")
    private int batchSize;

    @Value("${akuunda.wallet.monitor.threshold:0.01}")
    private double threshold;

    @Value("${akuunda.wallet.monitor.interval-ms:30000}")
    private long pollingInterval;

    @PostConstruct
    public void init() {
        log.info("✅ WalletBalanceMonitorTask initialized — interval: {}ms, batch: {}, threshold: {} USDC",
                pollingInterval, batchSize, threshold);

        // Pré-charger les soldes depuis la base pour éviter les fausses alertes au démarrage
        try {
            List<Wallet> wallets = walletRepository.findAllWithFetch();
            for (Wallet wallet : wallets) {
                if (wallet.getAddress() != null && !wallet.getAddress().isBlank()) {
                    lastKnownBalances.put(wallet.getAddress(), wallet.getBalance());
                }
            }
            log.info("📦 {} wallet balances pre-loaded from DB", lastKnownBalances.size());
        } catch (Exception e) {
            log.warn("⚠️ Failed to pre-load wallet balances: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${akuunda.wallet.monitor.interval-ms:30000}")
    public void monitorWalletBalances() {
        List<Wallet> allWallets = walletRepository.findAllWithFetch();

        if (allWallets.isEmpty()) {
            log.debug("🔍 No wallets to monitor");
            return;
        }

        // ── Round-robin : sélectionner le batch courant ──
        int total = allWallets.size();
        int startIdx = currentIndex.get() % total;
        int endIdx = Math.min(startIdx + batchSize, total);

        List<Wallet> batch = allWallets.subList(startIdx, endIdx);

        // Avancer l'index pour le prochain cycle
        int nextIndex = endIdx >= total ? 0 : endIdx;
        currentIndex.set(nextIndex);

        log.debug("🔍 Monitoring wallets [{}-{}) / {} (batch of {})",
                startIdx, endIdx, total, batch.size());

        for (Wallet wallet : batch) {
            try {
                checkWalletBalance(wallet);
            } catch (Exception e) {
                log.warn("⚠️ Error monitoring wallet {}: {}", wallet.getAddress(), e.getMessage());
            }
        }
    }

    private void checkWalletBalance(Wallet wallet) {
        String address = wallet.getAddress();
        if (address == null || address.isBlank()) {
            return;
        }

        if (wallet.getUsers() == null) {
            return;
        }

        String username = wallet.getUsers().getUsername();

        // Lire le solde on-chain
        Double onChainBalance = paymentFactoryContractService.getUsdcBalance(address);
        if (onChainBalance == null) {
            log.debug("⚠️ Unable to read on-chain balance for {}", address);
            return;
        }

        // Solde précédemment connu
        double previousBalance = lastKnownBalances.getOrDefault(address, wallet.getBalance());
        double diff = onChainBalance - previousBalance;

        // Ignorer si changement sous le seuil
        if (Math.abs(diff) < threshold) {
            // Mettre à jour le cache même si pas de notif (sync silencieuse)
            lastKnownBalances.put(address, onChainBalance);
            return;
        }

        log.info("💡 Balance change detected for {} ({}): {} → {} (diff: {} USDC)",
                username, address,
                String.format("%.6f", previousBalance),
                String.format("%.6f", onChainBalance),
                String.format("%+.6f", diff));

        // Mettre à jour le solde en base
        updateWalletBalance(wallet, onChainBalance);

        // Mettre à jour le cache mémoire
        lastKnownBalances.put(address, onChainBalance);

        // Envoyer la notification push
        String formattedDiff = String.format("%.2f", Math.abs(diff));

        if (diff > 0) {
            notificationHelper.notifyWalletDeposit(username, formattedDiff, "USDC");
            log.info("🔔 Deposit notification sent to {} — +{} USDC", username, formattedDiff);
        } else {
            notificationHelper.notifyWalletWithdrawal(username, formattedDiff, "USDC");
            log.info("🔔 Withdrawal notification sent to {} — -{} USDC", username, formattedDiff);
        }
    }

    @Transactional
    protected void updateWalletBalance(Wallet wallet, Double newBalance) {
        try {
            wallet.setBalance(newBalance);
            walletRepository.saveAndFlush(wallet);
            log.debug("✅ Wallet balance synced for {}: {} USDC", wallet.getAddress(), newBalance);
        } catch (Exception e) {
            log.error("❌ Failed to update wallet balance for {}: {}",
                    wallet.getAddress(), e.getMessage(), e);
        }
    }
}
