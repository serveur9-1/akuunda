package org.akuunda.akuundawallet.keycloak.api.service;

import org.akuunda.akuundawallet.keycloak.api.dto.AfricanDto;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
public interface SmsService {

    /**
     * {@inheritDoc}
     */
    ResponseEntity<String> sendSms(final String message, final List<String> destinataires, final String type);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<AfricanDto> isAfrican(final String mssidn);
}
