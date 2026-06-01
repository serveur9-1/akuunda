package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/pro/settings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Settings")
public class BackofficeProSettingsController {

    @GetMapping("/company")
    @Operation(summary = "Infos entreprise (stub)")
    public ResponseEntity<ApiSuccess<Object>> company() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of()));
    }

    @PutMapping("/company")
    @Operation(summary = "Modifier infos entreprise (stub)")
    public ResponseEntity<ApiSuccess<Object>> updateCompany(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updated", true)));
    }

    @GetMapping("/profile")
    @Operation(summary = "Profil propriétaire (stub)")
    public ResponseEntity<ApiSuccess<Object>> profile() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Modifier profil (stub)")
    public ResponseEntity<ApiSuccess<Object>> updateProfile(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updated", true)));
    }

    @PutMapping("/password")
    @Operation(summary = "Changer mot de passe (stub)")
    public ResponseEntity<ApiSuccess<Object>> changePassword(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updated", true)));
    }

    @GetMapping("/2fa")
    @Operation(summary = "Statut 2FA (stub)")
    public ResponseEntity<ApiSuccess<Object>> twoFa() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("enabled", false)));
    }

    @PostMapping("/2fa/enable")
    @Operation(summary = "Activer 2FA (stub)")
    public ResponseEntity<ApiSuccess<Object>> enable2fa(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("enabled", true)));
    }

    @PostMapping("/2fa/disable")
    @Operation(summary = "Désactiver 2FA (stub)")
    public ResponseEntity<ApiSuccess<Object>> disable2fa(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("enabled", false)));
    }
}

