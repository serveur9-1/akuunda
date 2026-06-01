package org.akuunda.akuundawallet.wallet.service.infrastructure;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalUserCreateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface AkunndaUserClientService {

    /**
     * {@inheritDoc}
     */
    ResponseEntity<ExternalUserCreateResponse> createUSer(@Valid final String body);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<ExternalUserCreateResponse> getUserById(@Valid final String userId);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<String> createUserSigningMethod(@Valid final String userId, @Valid final String body);

}
