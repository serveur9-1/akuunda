package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/admin/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Roles & RBAC")
public class BackofficeAdminRolesController {

    @GetMapping
    @Operation(summary = "Liste des rôles avec permissions (stub)")
    public ResponseEntity<ApiSuccess<Object>> list() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of(
                "adminRoles", List.of(),
                "proRoles", List.of()
        )));
    }

    @GetMapping("/{role}/permissions")
    @Operation(summary = "Permissions d'un rôle (stub)")
    public ResponseEntity<ApiSuccess<Object>> permissions(@PathVariable String role) {
        return ResponseEntity.ok(ApiSuccess.of(List.of()));
    }

    @PutMapping("/{role}/permissions")
    @Operation(summary = "Modifier permissions d'un rôle (stub)")
    public ResponseEntity<ApiSuccess<Object>> updatePermissions(@PathVariable String role, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("role", role, "updated", true)));
    }

    @GetMapping("/audit-log")
    @Operation(summary = "Journal RBAC (stub)")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Object>>> audit(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiSuccess.of(PaginatedResponse.of(Collections.emptyList(), page, limit, 0)));
    }
}

