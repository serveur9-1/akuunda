package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job planifié : passe les opérations restées "En cours" (PENDING, INITIALED, NEW, EN ATTENTE)
 * en statut REJETEE (affiché "Échec" dans l'historique) après un délai configurable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationTimeoutScheduler {

    private static final String STATUS_REJETEE = "REJETEE";
    private static final List<String> PENDING_STATUSES = List.of(
            "PENDING", "INITIALED", "NEW", "EN ATTENTE"
    );

    private final OperationRepository operationRepository;

    /**
     * Délai en heures au-delà duquel une opération "en cours" est considérée comme échouée.
     * Valeur par défaut : 24 heures.
     */
    @Value("${akuunda.operations.pending-timeout-hours:24}")
    private int pendingTimeoutHours;

    @Scheduled(cron = "${akuunda.operations.timeout-cron:0 0 * * * ?}") // Par défaut : toutes les heures
    @Transactional
    public void markStalePendingOperationsAsFailed() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(pendingTimeoutHours);
        List<Operation> stale = operationRepository.findByStatusInAndCreatedAtBefore(PENDING_STATUSES, cutoff);
        if (stale.isEmpty()) {
            log.debug("Aucune opération en attente expirée à traiter");
            return;
        }
        for (Operation op : stale) {
            // Protéger contre les opérations sans operationHash (données historiques)
            if (op.getOperationHash() == null || op.getOperationHash().isEmpty()) {
                op.setOperationHash("TIMEOUT-" + op.getId() + "-" + System.currentTimeMillis());
                log.warn("⚠️ Opération id={} sans operationHash, hash généré automatiquement", op.getId());
            }
            op.setStatus(STATUS_REJETEE);
            op.setUpdatedAt(LocalDateTime.now());
            operationRepository.save(op);
            log.info("Opération {} (id={}, designation={}) passée en REJETEE (timeout {}h)", 
                    op.getOperationHash(), op.getId(), op.getDesignation(), pendingTimeoutHours);
        }
        log.info("{} opération(s) passée(s) en échec (timeout)", stale.size());
    }
}
