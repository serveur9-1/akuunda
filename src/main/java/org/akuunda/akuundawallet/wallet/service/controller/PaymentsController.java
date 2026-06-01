package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentCreateRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentResponse;
import org.akuunda.akuundawallet.wallet.api.dto.RefundRequest;
import org.akuunda.akuundawallet.wallet.service.PaymentsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API publique « Payments » destinée aux intégrateurs marchand.
 * Auth : Authorization: Bearer sk_live_... (préféré) ou X-API-Key.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Création et lecture de paiements via clé API marchand")
public class PaymentsController {

    private final PaymentsService paymentsService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer un paiement",
            description = "Crée un paiement et retourne une URL de page hébergée à présenter au client. "
                        + "Le header Idempotency-Key (optionnel) permet de rejouer la même requête sans dupliquer.")
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid PaymentCreateRequest request) {
        String apiKey = resolveApiKey(authorization, apiKeyHeader);
        return paymentsService.createPayment(apiKey, idempotencyKey, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire un paiement",
            description = "Retourne le statut courant d'un paiement.")
    public ResponseEntity<PaymentResponse> get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @PathVariable String id) {
        String apiKey = resolveApiKey(authorization, apiKeyHeader);
        return paymentsService.getPayment(apiKey, id);
    }

    @PostMapping(path = "/{id}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rembourser un paiement",
            description = "Rembourse un paiement payé (total par défaut, partiel si `amount` est fourni). "
                        + "Déclenche un webhook `payment.refunded`.")
    public ResponseEntity<PaymentResponse> refund(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @PathVariable String id,
            @RequestBody(required = false) RefundRequest request) {
        String apiKey = resolveApiKey(authorization, apiKeyHeader);
        return paymentsService.refundPayment(apiKey, id, request);
    }

    @PostMapping(path = "/{id}/_test/complete")
    @Operation(summary = "[Sandbox] Simuler un paiement réussi",
            description = "Disponible uniquement avec une clé `sk_test_…` (mode test). "
                        + "Marque le paiement comme `paid` et déclenche le webhook `payment.completed`.")
    public ResponseEntity<PaymentResponse> testComplete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @PathVariable String id) {
        String apiKey = resolveApiKey(authorization, apiKeyHeader);
        return paymentsService.simulateTestCompletion(apiKey, id);
    }

    /** Accepte « Authorization: Bearer sk_live_… » ou « X-API-Key: sk_live_… ». */
    private String resolveApiKey(String authorization, String apiKeyHeader) {
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.trim();
        }
        if (authorization != null && !authorization.isBlank()) {
            String trimmed = authorization.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return trimmed.substring(7).trim();
            }
            return trimmed;
        }
        return null;
    }
}
