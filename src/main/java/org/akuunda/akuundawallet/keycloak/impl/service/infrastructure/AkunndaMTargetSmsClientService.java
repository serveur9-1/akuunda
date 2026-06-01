package org.akuunda.akuundawallet.keycloak.impl.service.infrastructure;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface AkunndaMTargetSmsClientService {

    ResponseEntity<String> SendSimpleSms(final String msg, final String msisdn);
    void SendUnicodeSms(final String msg, final String msisdn);
}
