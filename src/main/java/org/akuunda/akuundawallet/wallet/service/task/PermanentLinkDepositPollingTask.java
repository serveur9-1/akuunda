package org.akuunda.akuundawallet.wallet.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.CheckoutSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.CheckoutSession;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.akuunda.akuundawallet.wallet.service.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tâche planifiée qui vérifie périodiquement si les USDC sont arrivés sur les wallets CREATE2
 * pour les sessions de liens permanents, puis appelle executePayment() pour les transférer au wallet du marchand.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermanentLinkDepositPollingTask {

    private final PermanentLinkSessionRepository permanentLinkSessionRepository;
    private final PaymentFactoryContractService paymentFactoryContractService;
    private final WalletRepository walletRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final WebhookService webhookService;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String adminWalletId;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String adminWalletAddress;

    @Scheduled(fixedDelayString = "${akuunda.permanent-link.deposit.polling.interval-ms:30000}")
    @Transactional
    public void pollPendingPermanentLinkSessions() {
        List<PermanentLinkSession> pendingSessions = permanentLinkSessionRepository
                .findByStatusIn(List.of("PENDING"));

        if (pendingSessions.isEmpty()) return;

        log.info("🔍 Polling {} pending permanent link sessions", pendingSessions.size());

        Wallet adminWallet = getAdminWallet();
        if (adminWallet == null) {
            log.error("❌ Admin wallet not configured, cannot execute payments");
            return;
        }

        for (PermanentLinkSession session : pendingSessions) {
            try {
                // Sessions liées à un contrat partenaire → gérées par PartnerPaymentMonitorScheduler
                if (session.getPartnerContractPaymentCode() != null) {
                    continue;
                }

                // 1. Check expiration
                if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
                    log.warn("⏰ Permanent link session expired: {}", session.getSessionCode());
                    session.setStatus("EXPIRED");
                    permanentLinkSessionRepository.save(session);
                    continue;
                }

                // 2. Check paymentIdBytes32
                if (session.getPaymentIdBytes32() == null || session.getPaymentIdBytes32().isEmpty()) {
                    log.warn("⚠️ paymentIdBytes32 not set for session: {}", session.getSessionCode());
                    continue;
                }

                // 3. Check CREATE2 address
                String create2Address = session.getCreate2WalletAddress();
                if (create2Address == null || create2Address.isEmpty()) {
                    log.warn("⚠️ CREATE2 address not set for session: {}", session.getSessionCode());
                    continue;
                }

                // 4. Check USDC balance
                Double usdcBalance = paymentFactoryContractService.getUsdcBalance(create2Address);
                log.info("💰 USDC balance on CREATE2 {} for session {}: {} USDC",
                        create2Address, session.getSessionCode(), usdcBalance);

                boolean received = usdcBalance != null && usdcBalance > 0;

                if (received) {
                    log.info("✅ USDC received on CREATE2 wallet for permanent link session: {}",
                            session.getSessionCode());

                    // 5. Execute payment to transfer to merchant wallet
                    String txHash = paymentFactoryContractService.executePayment(
                            session.getPaymentIdBytes32(),
                            adminWallet
                    );

                    if (txHash != null && !txHash.startsWith("PAYMENT-FAILED")) {
                        session.setStatus("PAID");
                        session.setPaidAt(LocalDateTime.now());
                        session.setExecuteTxHash(txHash);
                        permanentLinkSessionRepository.save(session);
                        log.info("✅ Payment executed for permanent link session: {}. Tx: {}",
                                session.getSessionCode(), txHash);

                        // Check if this payment session is linked to a checkout
                        CheckoutSession checkoutSession = checkoutSessionRepository.findByPaymentSession(session).orElse(null);
                        if (checkoutSession != null && !checkoutSession.getWebhookSent()) {
                            checkoutSession.setStatus("PAID");
                            checkoutSession.setPaidAt(LocalDateTime.now());
                            checkoutSessionRepository.save(checkoutSession);
                            webhookService.sendPaymentWebhook(checkoutSession);
                        }
                    } else {
                        log.error("❌ executePayment failed for permanent link session: {}",
                                session.getSessionCode());
                        session.setStatus("FAILED");
                        permanentLinkSessionRepository.save(session);
                    }
                }

            } catch (Exception e) {
                log.error("Error polling permanent link session: {}", session.getSessionCode(), e);
            }
        }
    }

    /**
     * Traitement déclenché par le listener ERC20 (événement Transfer temps réel).
     * Cherche la PermanentLinkSession par adresse CREATE2 et exécute le paiement.
     */
    @Transactional
    public void processSessionByCreate2Address(String create2Address, Double usdcAmount) {
        PermanentLinkSession session = permanentLinkSessionRepository
                .findByCreate2WalletAddress(create2Address).orElse(null);
        if (session == null) {
            log.warn("[EventTrigger] Aucune PermanentLinkSession pour: {}", create2Address);
            return;
        }
        // Sessions liées à un contrat partenaire → gérées par PartnerPaymentMonitorScheduler
        if (session.getPartnerContractPaymentCode() != null) return;
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus("EXPIRED");
            permanentLinkSessionRepository.save(session);
            return;
        }
        Wallet adminWallet = getAdminWallet();
        if (adminWallet == null) {
            log.error("[EventTrigger] Admin wallet introuvable");
            return;
        }
        log.info("[EventTrigger] Traitement PermanentLinkSession {} — {} USDC",
                session.getSessionCode(), usdcAmount);

        // Exécuter le paiement pour transférer les fonds au wallet du marchand
        String txHash = paymentFactoryContractService.executePayment(
                session.getPaymentIdBytes32(), adminWallet);

        if (txHash != null && !txHash.startsWith("PAYMENT-FAILED")) {
            session.setStatus("PAID");
            session.setPaidAt(LocalDateTime.now());
            session.setExecuteTxHash(txHash);
            permanentLinkSessionRepository.save(session);
            log.info("[EventTrigger] Paiement exécuté pour la session {}, tx={}",
                    session.getSessionCode(), txHash);

            // Vérifier si cette session est liée à un checkout
            CheckoutSession checkoutSession = checkoutSessionRepository
                    .findByPaymentSession(session).orElse(null);
            if (checkoutSession != null && !checkoutSession.getWebhookSent()) {
                checkoutSession.setStatus("PAID");
                checkoutSession.setPaidAt(LocalDateTime.now());
                checkoutSessionRepository.save(checkoutSession);
                webhookService.sendPaymentWebhook(checkoutSession);
            }
        } else {
            log.error("[EventTrigger] executePayment échoué pour la session: {}",
                    session.getSessionCode());
            session.setStatus("FAILED");
            permanentLinkSessionRepository.save(session);
        }
    }

    private Wallet getAdminWallet() {
        if (adminWalletId != null && !adminWalletId.isEmpty()) {
            return walletRepository.findByIdWithFetch(adminWalletId).orElse(null);
        }
        if (adminWalletAddress != null && !adminWalletAddress.isEmpty()) {
            return walletRepository.findByAddress(adminWalletAddress);
        }
        return null;
    }
}
