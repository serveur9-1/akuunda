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
@RequestMapping(path = "/api/v1/admin/esim", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin eSIM")
public class BackofficeAdminEsimController {

    @GetMapping
    @Operation(summary = "Liste des eSIM (stub)")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(ApiSuccess.of(PaginatedResponse.of(Collections.emptyList(), page, limit, 0)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail eSIM (stub)")
    public ResponseEntity<ApiSuccess<Object>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "status", "inactive")));
    }

    @PostMapping
    @Operation(summary = "Provisionner eSIM (stub)")
    public ResponseEntity<ApiSuccess<Object>> provision(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("status", "provisioning")));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activer (stub)")
    public ResponseEntity<ApiSuccess<Object>> activate(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "status", "active")));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspendre (stub)")
    public ResponseEntity<ApiSuccess<Object>> suspend(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "status", "suspended")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer (stub)")
    public ResponseEntity<ApiSuccess<Object>> delete(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "deleted", true)));
    }

    @PostMapping("/{id}/send-qr")
    @Operation(summary = "Envoyer QR (stub)")
    public ResponseEntity<ApiSuccess<Object>> sendQr(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "sent", true)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Stats eSIM (stub)")
    public ResponseEntity<ApiSuccess<Object>> stats() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("total", 0, "active", 0, "suspended", 0)));
    }

    @GetMapping("/usage")
    @Operation(summary = "Usage agrégé (stub)")
    public ResponseEntity<ApiSuccess<Object>> usage(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(ApiSuccess.of(Collections.emptyList()));
    }
}

