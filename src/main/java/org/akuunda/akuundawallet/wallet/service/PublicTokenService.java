package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.PublicTokenResponse;
import org.springframework.http.ResponseEntity;

public interface PublicTokenService {
    ResponseEntity<PublicTokenResponse> generatePublicToken();
}
