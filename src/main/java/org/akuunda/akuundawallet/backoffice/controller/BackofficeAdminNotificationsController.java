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
@RequestMapping(path = "/api/v1/admin/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Notifications")
public class BackofficeAdminNotificationsController {

    @GetMapping
    @Operation(summary = "Liste notifications admin (stub)")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Boolean read) {
        return ResponseEntity.ok(ApiSuccess.of(PaginatedResponse.of(Collections.emptyList(), page, limit, 0)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer comme lu (stub)")
    public ResponseEntity<ApiSuccess<Object>> markRead(@PathVariable String id) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "read", true)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Tout marquer comme lu (stub)")
    public ResponseEntity<ApiSuccess<Object>> readAll() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updated", 0)));
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Envoyer notification à un segment (stub)")
    public ResponseEntity<ApiSuccess<Object>> broadcast(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("status", "queued")));
    }
}

