package org.akuunda.akuundawallet.wallet.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Public endpoint that redirects to the payment provider (Mercuryo, etc.).
 * The actual provider URL containing sensitive data (wallet address, API keys)
 * is stored server-side and never exposed directly to the frontend.
 */
@RestController
@RequestMapping("/api/internal/v1/pay-redirect")
@Slf4j
@RequiredArgsConstructor
public class PayRedirectController {

    private final PermanentLinkSessionRepository permanentLinkSessionRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;

    /**
     * Redirect to payment provider for permanent link sessions.
     */
    @GetMapping("/{sessionCode}")
    public ResponseEntity<Void> payRedirectPermanentLink(@PathVariable String sessionCode) {
        log.info("Pay redirect requested for permanent link session: {}", sessionCode);

        var sessionOpt = permanentLinkSessionRepository.findBySessionCode(sessionCode);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var session = sessionOpt.get();

        if (session.getProviderWidgetUrl() == null || session.getProviderWidgetUrl().isBlank()) {
            log.warn("No provider widget URL stored for session: {}", sessionCode);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", session.getProviderWidgetUrl())
                .build();
    }

    /**
     * Redirect to payment provider for one-time payment links.
     */
    @GetMapping("/otpl/{uniqueCode}")
    public ResponseEntity<Void> payRedirectOneTimeLink(@PathVariable String uniqueCode) {
        log.info("Pay redirect requested for one-time payment link: {}", uniqueCode);

        var linkOpt = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode);
        if (linkOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var link = linkOpt.get();

        if (link.getProviderWidgetUrl() == null || link.getProviderWidgetUrl().isBlank()) {
            log.warn("No provider widget URL stored for link: {}", uniqueCode);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", link.getProviderWidgetUrl())
                .build();
    }
}
