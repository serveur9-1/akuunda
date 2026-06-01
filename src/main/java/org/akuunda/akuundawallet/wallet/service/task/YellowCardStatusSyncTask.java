package org.akuunda.akuundawallet.wallet.service.task;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Synchronise le statut des opérations YellowCard non terminales
 * en interrogeant l'API YellowCard au démarrage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YellowCardStatusSyncTask {

    private final OperationRepository operationRepository;
    private final AkuundaYellowCardClientService yellowCardClientService;

    private static final List<String> NON_TERMINAL_AND_RAW = List.of(
            // Statuts internes non terminaux
            "EN_COURS", "PENDING",
            // Statuts bruts YellowCard potentiellement stockés
            "complete", "completed", "successful",
            "pending", "processing", "failed", "cancelled", "expired", "refunded"
    );

    @PostConstruct
    public void syncOnStartup() {
        try {
            List<Operation> ops = operationRepository.findByStatusIn(NON_TERMINAL_AND_RAW).stream()
                    .filter(op -> op.getProvider() != null
                            && op.getProvider().toLowerCase().contains("yellow")
                            && op.getOperationHash() != null
                            && op.getUsername() != null)
                    .toList();

            if (ops.isEmpty()) return;

            log.info("🔄 YellowCard sync au démarrage: {} opération(s) à vérifier", ops.size());
            int updated = 0;
            for (Operation op : ops) {
                try {
                    ResponseEntity<?> resp = yellowCardClientService
                            .checkTransactionStatus(op.getOperationHash(), op.getUsername());
                    if (resp.getStatusCode() == HttpStatus.OK) {
                        updated++;
                    } else {
                        log.warn("⚠️ Sync YC: statut {} pour op #{} (hash={})",
                                resp.getStatusCode(), op.getId(), op.getOperationHash());
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Sync YC: erreur op #{}: {}", op.getId(), e.getMessage());
                }
            }
            log.info("✅ YellowCard sync terminée: {}/{} opération(s) synchronisée(s)", updated, ops.size());
        } catch (Exception e) {
            log.error("❌ Erreur sync YellowCard au démarrage: {}", e.getMessage(), e);
        }
    }
}
