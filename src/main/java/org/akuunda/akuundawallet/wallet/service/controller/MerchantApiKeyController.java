package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantApiKeyRequest;
import org.akuunda.akuundawallet.wallet.service.MerchantApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/v1/merchant-keys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Merchant API Keys", description = "Gestion des clés API marchands pour l'intégration e-commerce")
public class MerchantApiKeyController {

    private final MerchantApiKeyService merchantApiKeyService;

    @PostMapping("/create")
    public ResponseEntity<?> createApiKey(
            @RequestParam String username,
            @RequestParam String merchantSlug,
            @RequestBody(required = false) MerchantApiKeyRequest request) {
        return merchantApiKeyService.createApiKey(username, merchantSlug, request);
    }

    @GetMapping("/list")
    public ResponseEntity<?> listApiKeys(@RequestParam String username) {
        return merchantApiKeyService.listApiKeys(username);
    }

    @DeleteMapping("/{apiKeyId}")
    public ResponseEntity<?> revokeApiKey(
            @RequestParam String username,
            @PathVariable Long apiKeyId) {
        return merchantApiKeyService.revokeApiKey(username, apiKeyId);
    }
}
