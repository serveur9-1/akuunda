package org.akuunda.akuundawallet.wallet.service.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Nettoyage en masse des paiements abandonnés.
 *
 * <p>Deux cas couverts :
 * <ol>
 *   <li>expiresAt explicite dépassé → expire immédiatement</li>
 *   <li>expiresAt absent mais créé il y a plus de {@code fallbackExpiryHours} heures → expire aussi</li>
 * </ol>
 *
 * <p>Utilise des UPDATE directs en base (pas de chargement en mémoire) : une seule requête SQL
 * quelle que soit la quantité de records, ce qui évite le problème du polling qui chargeait
 * tous les liens CREATED/PENDING à chaque cycle pour vérifier l'expiration un par un.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredPaymentCleanupTask {

    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final PermanentLinkSessionRepository permanentLinkSessionRepository;

    @Value("${akuunda.payment.cleanup.fallback-expiry-hours:48}")
    private int fallbackExpiryHours;

    @Scheduled(fixedDelayString = "${akuunda.payment.cleanup.interval-ms:3600000}")
    @Transactional
    public void expireAbandonedPayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fallback = now.minusHours(fallbackExpiryHours);

        // ── OneTimePaymentLink ──
        int otpByDate = oneTimePaymentLinkRepository.bulkExpireByExpiresAt(now);
        int otpOrphaned = oneTimePaymentLinkRepository.bulkExpireOrphaned(now, fallback);
        if (otpByDate + otpOrphaned > 0) {
            log.info("[Cleanup] OneTimePaymentLink expirés: {} par expiresAt, {} abandonnés (>{}h sans date)",
                    otpByDate, otpOrphaned, fallbackExpiryHours);
        }

        // ── PermanentLinkSession ──
        int plsByDate = permanentLinkSessionRepository.bulkExpireByExpiresAt(now);
        int plsOrphaned = permanentLinkSessionRepository.bulkExpireOrphaned(now, fallback);
        if (plsByDate + plsOrphaned > 0) {
            log.info("[Cleanup] PermanentLinkSession expirées: {} par expiresAt, {} abandonnées (>{}h sans date)",
                    plsByDate, plsOrphaned, fallbackExpiryHours);
        }

        int total = otpByDate + otpOrphaned + plsByDate + plsOrphaned;
        if (total == 0) {
            log.debug("[Cleanup] Aucun paiement abandonné à expirer.");
        }
    }
}
