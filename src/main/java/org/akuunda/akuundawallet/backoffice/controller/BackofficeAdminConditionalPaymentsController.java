package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.dto.admin.ConditionalPaymentDto;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAdminService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/v1/admin/conditional-payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Conditional Payments")
@RequiredArgsConstructor
public class BackofficeAdminConditionalPaymentsController {

    private final BackofficeAdminService backofficeAdminService;

    @GetMapping
    @Operation(summary = "Liste des paiements conditionnels")
    public ResponseEntity<ApiSuccess<PaginatedResponse<ConditionalPaymentDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status) {
        PaginatedResponse<ConditionalPaymentDto> data = backofficeAdminService.getConditionalPayments(
                PageRequest.of(Math.max(0, page - 1), Math.min(100, limit)), status);
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Détail d'un paiement conditionnel")
    public ResponseEntity<ApiSuccess<ConditionalPaymentDto>> get(@PathVariable String paymentId) {
        ConditionalPaymentDto data = backofficeAdminService.getConditionalPaymentById(paymentId);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques escrow")
    public ResponseEntity<ApiSuccess<Object>> getStats(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(ApiSuccess.of(java.util.Map.of("totalEscrow", 0, "activeEscrow", 0, "currency", "USDC")));
    }
}
