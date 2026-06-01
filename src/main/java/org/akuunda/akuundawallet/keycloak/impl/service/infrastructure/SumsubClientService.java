package org.akuunda.akuundawallet.keycloak.impl.service.infrastructure;

import org.akuunda.akuundawallet.keycloak.api.dto.external.sumsub.AccessToken;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface SumsubClientService {

    ResponseEntity<AccessToken> getAccessToken(final String username, final String levelName);
}
