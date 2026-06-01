package org.akuunda.akuundawallet.keycloak.api.service;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.keycloak.api.dto.AuthenticationRequest;
import org.akuunda.akuundawallet.keycloak.api.dto.AuthenticationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface AuthenticationService {
    ResponseEntity<AuthenticationResponse> generateToken(@Valid AuthenticationRequest loginRequest, String realmName);
}
