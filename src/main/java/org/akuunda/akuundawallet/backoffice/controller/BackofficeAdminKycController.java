package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/admin/kyc", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin KYC")
public class BackofficeAdminKycController {

    @GetMapping
    @Operation(summary = "Liste demandes KYC (stub)")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiSuccess.of(PaginatedResponse.of(Collections.emptyList(), page, limit, 0)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail demande KYC (stub)")
    public ResponseEntity<ApiSuccess<Object>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "status", "pending")));
    }

    @GetMapping("/{id}/documents")
    @Operation(summary = "Télécharger documents KYC (stub)")
    public ResponseEntity<ApiSuccess<Object>> documents(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(Collections.emptyList()));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Approuver (stub)")
    public ResponseEntity<ApiSuccess<Object>> approve(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "status", "approved")));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Rejeter (stub)")
    public ResponseEntity<ApiSuccess<Object>> reject(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "status", "rejected")));
    }

    @GetMapping("/stats")
    @Operation(summary = "Stats KYC (stub)")
    public ResponseEntity<ApiSuccess<Object>> stats() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of(
                "pending", 0,
                "approved", 0,
                "rejected", 0
        )));
    }
}

