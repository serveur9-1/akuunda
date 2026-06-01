package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.dto.admin.PaymentLinkDto;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAdminService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/v1/admin/payment-links", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Payment Links")
@RequiredArgsConstructor
public class BackofficeAdminPaymentLinksController {

    private final BackofficeAdminService backofficeAdminService;

    @GetMapping
    @Operation(summary = "Liste des payment links")
    public ResponseEntity<ApiSuccess<PaginatedResponse<PaymentLinkDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status) {
        PaginatedResponse<PaymentLinkDto> data = backofficeAdminService.getPaymentLinks(
                PageRequest.of(Math.max(0, page - 1), Math.min(100, limit)), status);
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @GetMapping("/{linkId}")
    @Operation(summary = "Détail d'un payment link")
    public ResponseEntity<ApiSuccess<PaymentLinkDto>> get(@PathVariable String linkId) {
        PaymentLinkDto data = backofficeAdminService.getPaymentLinkById(linkId);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques payment links")
    public ResponseEntity<ApiSuccess<Object>> getStats(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(ApiSuccess.of(java.util.Map.of(
                "totalLinks", 0,
                "activeLinks", 0,
                "totalCollected", 0,
                "currency", "XOF"
        )));
    }
}
