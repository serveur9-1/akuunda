package org.akuunda.akuundawallet.wallet.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OperationExpirationScheduler {

    private final OperationRepository operationRepository;

    /**
     * Toutes les 30 minutes, passe les opérations PENDING ou EN_COURS
     * de plus de 4 heures au statut ECHOUEE.
     */
    @Scheduled(fixedRateString = "${akuunda.operation.expiration.interval-ms:1800000}")
    public void expireStaleOperations() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(4);

        List<Operation> staleOps = operationRepository
                .findByStatusInAndCreatedAtBefore(List.of("PENDING", "EN_COURS"), cutoff);

        if (staleOps.isEmpty()) {
            return;
        }

        log.info("⏰ {} opération(s) PENDING/EN_COURS de plus de 4h détectée(s), passage à ECHOUEE", staleOps.size());

        for (Operation op : staleOps) {
            String oldStatus = op.getStatus();
            op.setStatus("ECHOUEE");
            op.setUpdatedAt(LocalDateTime.now());
            operationRepository.save(op);

            log.info("⏰ Opération expirée: id={}, hash={}, {} → ECHOUEE (créée à {}, soit > 4h)",
                    op.getId(), op.getOperationHash(), oldStatus, op.getCreatedAt());
        }

        operationRepository.flush();
        log.info("✅ {} opération(s) expirée(s) mises à jour", staleOps.size());
    }
}
