package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.PublicTokenResponse;
import org.akuunda.akuundawallet.wallet.service.PublicTokenService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = "/api/internal/v1/auth/public", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Public Authentication", description = "Token generation for public payment pages (no credentials required from client)")
@RequiredArgsConstructor
public class PublicAuthController {

    private final PublicTokenService publicTokenService;

    @GetMapping("/token")
    @Operation(
            operationId = "getPublicToken",
            summary = "Get a public access token (no credentials needed)",
            description = "Generates a Keycloak access token using server-side credentials. Used by the public payment page (qr.akuunda-pay.io) to authenticate API calls without exposing credentials in the frontend."
    )
    public ResponseEntity<PublicTokenResponse> getPublicToken() {
        log.info("Received request for public token");
        return publicTokenService.generatePublicToken();
    }
}
