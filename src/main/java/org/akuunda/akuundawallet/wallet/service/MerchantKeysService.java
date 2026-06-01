package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyCreateRequest;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyItem;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Gestion des clés API marchand. Auth : JWT du dashboard.
 * Le marchand est identifié automatiquement par le JWT — pas besoin de username/slug.
 */
public interface MerchantKeysService {

    /**
     * Crée une clé API. Renvoie {@link org.akuunda.akuundawallet.common.Exceptions.ErrorResponse}
     * en cas de refus (403, 422), sinon {@link MerchantKeyResponse}.
     */
    ResponseEntity<?> createKey(Jwt jwt, MerchantKeyCreateRequest request);

    ResponseEntity<List<MerchantKeyItem>> listKeys(Jwt jwt);

    ResponseEntity<Void> revokeKey(Jwt jwt, Long keyId);
}
