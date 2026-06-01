package org.akuunda.akuundawallet.keycloak.api.service;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
public interface SigningMethodService {

    /**
     * Save an signing method
     */
    ResponseEntity<String> createSigning (final String methodValue, final String methodType, final String userName, final String signingPin, final String physicalDeviceId);

    /**
     * Get all signing methods
     */
    ResponseEntity<List<ExternalSigningMethod>> getAllSigningMethods(@Valid String userName);
}
