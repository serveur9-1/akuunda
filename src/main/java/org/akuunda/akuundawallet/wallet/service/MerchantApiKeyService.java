package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.MerchantApiKeyRequest;
import org.springframework.http.ResponseEntity;

public interface MerchantApiKeyService {
    ResponseEntity<?> createApiKey(String username, String merchantSlug, MerchantApiKeyRequest request);
    ResponseEntity<?> listApiKeys(String username);
    ResponseEntity<?> revokeApiKey(String username, Long apiKeyId);
}
