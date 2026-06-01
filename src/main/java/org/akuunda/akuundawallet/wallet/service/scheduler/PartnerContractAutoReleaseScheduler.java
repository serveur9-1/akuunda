package org.akuunda.akuundawallet.wallet.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.entities.PartnerContractPayment;
import org.akuunda.akuundawallet.wallet.service.impl.PartnerContractServiceImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler pour les modes TIME_BASED et DISPUTE_WINDOW.
 *
 * Toutes les 5 minutes, cherche les paiements dont l'heure de libération automatique
 * est dépassée et qui n'ont pas été contestés, et déclenche la redistribution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerContractAutoReleaseScheduler {

    private final PartnerContractPaymentRepository paymentRepository;
    private final PartnerContractServiceImpl partnerContractService;

    @Scheduled(fixedDelayString = "${akuunda.partner.auto-release.interval-ms:300000}")
    public void processAutoReleases() {
        List<PartnerContractPayment> due = paymentRepository
                .findByStatusAndAutoReleaseScheduledAtBeforeAndDisputedAtIsNull(
                        "pending_condition", LocalDateTime.now());

        if (due.isEmpty()) return;

        log.info("Auto-release scheduler: {} paiement(s) éligible(s) à libérer", due.size());

        for (PartnerContractPayment payment : due) {
            try {
                String mode = payment.getContract().getTriggerType();
                log.info("Libération automatique ({}): paymentCode={}, scheduledAt={}",
                        mode, payment.getPaymentCode(), payment.getAutoReleaseScheduledAt());

                payment.setStatus("condition_validated");
                paymentRepository.save(payment);
                partnerContractService.executeDistribution(payment);

                log.info("Libération automatique réussie: paymentCode={}", payment.getPaymentCode());
            } catch (Exception e) {
                log.error("Échec libération automatique: paymentCode={}, erreur={}",
                        payment.getPaymentCode(), e.getMessage(), e);
            }
        }
    }
}
