package org.akuunda.akuundawallet.wallet.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.entities.PartnerContractPayment;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.service.PartnerPaymentProcessingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Filet de sécurité : surveille les paiements en attente de réception USDC sur leur adresse CREATE2.
 *
 * <p>La détection primaire est assurée par {@link UsdcTransferEventListener} via WebSocket Polygon.
 * Ce scheduler intervient uniquement pour rattraper les paiements non captés par le listener
 * (déconnexion réseau, redémarrage, etc.).
 *
 * Flow :
 *   1. isPaymentReceived()  → USDC arrivé sur CREATE2 ?
 *   2. executePayment()     → déplace CREATE2 → wallet intermédiaire
 *   3. approveAndDeposit()  → intermédiaire → AkuundaConditionalPayment (fonds verrouillés)
 *
 * Tourne toutes les 30 minutes (filet de sécurité — non prioritaire).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerPaymentMonitorScheduler {

    private final PartnerContractPaymentRepository paymentRepository;
    private final WalletRepository walletRepository;
    private final PartnerPaymentProcessingService processingService;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String intermediateWalletAddress;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String intermediateWalletId;

    @Scheduled(fixedDelayString = "${akuunda.partner.payment.monitor.interval-ms:1800000}")
    @Transactional
    public void monitorPendingPayments() {
        log.info("[SAFETY NET] Vérification des paiements non captés par le listener");

        List<PartnerContractPayment> pending = paymentRepository
                .findByCreate2StatusAndStatusOrderByCreatedAtAsc("waiting_payment", "pending_condition");

        if (pending.isEmpty()) return;

        log.info("[SAFETY NET] Monitor CREATE2: {} paiement(s) en attente de réception", pending.size());

        Wallet serviceWallet = walletRepository.findByIdWithFetch(intermediateWalletId).orElse(null);
        if (serviceWallet == null) serviceWallet = walletRepository.findByAddress(intermediateWalletAddress);
        if (serviceWallet == null) {
            log.error("[SAFETY NET] Wallet de service introuvable — monitoring interrompu");
            return;
        }

        for (PartnerContractPayment payment : pending) {
            try {
                processingService.processPayment(payment, serviceWallet);
            } catch (Exception e) {
                log.error("[SAFETY NET] Erreur monitoring paiement {}: {}", payment.getPaymentCode(), e.getMessage(), e);
            }
        }
    }
}
