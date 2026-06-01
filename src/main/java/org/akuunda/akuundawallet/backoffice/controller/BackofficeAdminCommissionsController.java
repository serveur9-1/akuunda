package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/admin/commissions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Commissions")
public class BackofficeAdminCommissionsController {

    @GetMapping("/monthly")
    @Operation(summary = "Commissions par mois (stub)")
    public ResponseEntity<ApiSuccess<Object>> monthly(@RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiSuccess.of(List.of()));
    }

    @GetMapping("/by-type")
    @Operation(summary = "Commissions par type (stub)")
    public ResponseEntity<ApiSuccess<Object>> byType() {
        return ResponseEntity.ok(ApiSuccess.of(List.of()));
    }

    @GetMapping("/stats")
    @Operation(summary = "KPI commissions (stub)")
    public ResponseEntity<ApiSuccess<Object>> stats() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of(
                "total", 0,
                "avgRate", 0,
                "growth", 0
        )));
    }
}

