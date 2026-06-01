package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.service.BackofficeProMerchantResolver;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.keycloak.api.service.SigningMethodService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/pro/signing-methods", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Signing Methods")
@RequiredArgsConstructor
public class BackofficeProSigningMethodsController {

    private final BackofficeProMerchantResolver merchantResolver;
    private final SigningMethodService signingMethodService;

    @GetMapping
    @Operation(summary = "Méthodes de signature du marchand connecté (PIN, etc.)")
    public ResponseEntity<ApiSuccess<List<ExternalSigningMethod>>> list() {
        String username = merchantResolver.resolveWalletUsername();
        ResponseEntity<List<ExternalSigningMethod>> upstream =
                signingMethodService.getAllSigningMethods(username);
        return ResponseEntity.status(upstream.getStatusCode())
                .body(ApiSuccess.of(upstream.getBody() != null ? upstream.getBody() : List.of()));
    }
}
