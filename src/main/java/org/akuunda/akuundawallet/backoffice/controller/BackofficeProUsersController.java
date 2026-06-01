package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.keycloak.api.dto.UserResponse;
import org.akuunda.akuundawallet.keycloak.api.dto.external.UsernameRequest;
import org.akuunda.akuundawallet.keycloak.api.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Vérification destinataire (proxy {@code POST /api/internal/v1/users/{realm}/getUser}).
 */
@RestController
@RequestMapping(path = "/api/v1/pro/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Users")
@RequiredArgsConstructor
public class BackofficeProUsersController {

    private final UserService userService;

    @Value("${keycloak.realm:akuunda-realm}")
    private String keycloakRealm;

    @PostMapping("/getUser")
    @Operation(summary = "Vérifier / récupérer un utilisateur Akuunda par username ou téléphone")
    public ResponseEntity<UserResponse> getUser(@RequestBody @Valid UsernameRequest request) {
        return userService.getUser(request.getUsername());
    }
}
