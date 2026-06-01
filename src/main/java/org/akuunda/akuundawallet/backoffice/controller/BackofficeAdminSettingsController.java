package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/admin/settings", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Settings")
public class BackofficeAdminSettingsController {

    @GetMapping("/commissions")
    @Operation(summary = "Taux de commission actuels (stub)")
    public ResponseEntity<ApiSuccess<Object>> commissions() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of()));
    }

    @PutMapping("/commissions")
    @Operation(summary = "Modifier taux de commission (stub)")
    public ResponseEntity<ApiSuccess<Object>> updateCommissions(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updated", true)));
    }

    @GetMapping("/limits")
    @Operation(summary = "Limites de transaction (stub)")
    public ResponseEntity<ApiSuccess<Object>> limits() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of()));
    }

    @PutMapping("/limits")
    @Operation(summary = "Modifier limites (stub)")
    public ResponseEntity<ApiSuccess<Object>> updateLimits(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updated", true)));
    }

    @GetMapping("/general")
    @Operation(summary = "Paramètres généraux")
    public ResponseEntity<ApiSuccess<Object>> getGeneral() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("platformName", "Akuunda Pay", "defaultCurrency", "XOF")));
    }

    @PutMapping("/general")
    @Operation(summary = "Modifier paramètres généraux (stub)")
    public ResponseEntity<ApiSuccess<Object>> updateGeneral(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("updated", true)));
    }

    @GetMapping("/security")
    @Operation(summary = "Paramètres sécurité")
    public ResponseEntity<ApiSuccess<Object>> getSecurity() {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("twoFactorRequired", false)));
    }
}
