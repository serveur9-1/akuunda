package org.akuunda.akuundawallet.wallet.service.task;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.KyrrexTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransactionType;
import org.akuunda.akuundawallet.wallet.service.KyrrexDepositSettlementService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Filet de sécurité du flux Kyrrex -> Akuunda Pay :
 * scanne périodiquement les dépôts en statut terminal de succès qui n'ont pas
 * encore été crédités sur le wallet utilisateur (webhook perdu, erreur
 * transitoire, redémarrage du service…), et tente de les régler de nouveau.
 *
 * <p>Activé via {@code akuunda.kyrrex.settlement.reconciliation.enabled}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KyrrexSettlementReconciliationTask {

    private static final List<KyrrexTransactionType> DEPOSIT_TYPES = List.of(
            KyrrexTransactionType.DEPOSIT,
            KyrrexTransactionType.ON_RAMP,
            KyrrexTransactionType.ADVANCED_EXCHANGE
    );

    private static final List<String> SUCCESS_STATUSES = List.of("SUCCESS", "VALIDEE", "done");

    private final KyrrexTransactionRepository kyrrexTransactionRepository;
    private final KyrrexDepositSettlementService settlementService;

    @Value("${akuunda.kyrrex.settlement.reconciliation.enabled:true}")
    private boolean enabled;

    @Value("${akuunda.kyrrex.settlement.reconciliation.batch-size:25}")
    private int batchSize;

    @Value("${akuunda.kyrrex.settlement.reconciliation.interval-ms:300000}")
    private long intervalMs;

    @PostConstruct
    public void init() {
        if (enabled) {
            log.info("✅ KyrrexSettlementReconciliationTask actif — interval={}ms, batchSize={}",
                    intervalMs, batchSize);
        } else {
            log.info("⏸️ KyrrexSettlementReconciliationTask désactivé");
        }
    }

    @Scheduled(fixedDelayString = "${akuunda.kyrrex.settlement.reconciliation.interval-ms:300000}",
            initialDelayString = "${akuunda.kyrrex.settlement.reconciliation.initial-delay-ms:60000}")
    public void reconcile() {
        if (!enabled) return;

        List<KyrrexTransaction> pending;
        try {
            pending = kyrrexTransactionRepository
                    .findByTypeInAndStatusInAndSettledAtIsNull(DEPOSIT_TYPES, SUCCESS_STATUSES);
        } catch (Exception e) {
            log.error("❌ [KYRREX-RECON] Erreur fetch transactions à réconcilier: {}", e.getMessage(), e);
            return;
        }

        if (pending.isEmpty()) {
            log.debug("[KYRREX-RECON] Aucun dépôt à réconcilier");
            return;
        }

        int total = Math.min(pending.size(), batchSize);
        log.info("🔁 [KYRREX-RECON] {} dépôts en attente de règlement, traitement de {}",
                pending.size(), total);

        int settled = 0;
        int failed = 0;
        for (int i = 0; i < total; i++) {
            KyrrexTransaction tx = pending.get(i);
            try {
                if (settlementService.settleIfEligible(tx)) {
                    settled++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("⚠️ [KYRREX-RECON] Échec règlement uid={}: {}", tx.getKyrrexId(), e.getMessage());
            }
        }

        if (settled > 0 || failed > 0) {
            log.info("✅ [KYRREX-RECON] Cycle terminé — réglés={}, échecs={}, restants={}",
                    settled, failed, Math.max(0, pending.size() - total));
        }
    }
}
