package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface TokenGenerationService {

    ResponseEntity<String> generateToken();
}
