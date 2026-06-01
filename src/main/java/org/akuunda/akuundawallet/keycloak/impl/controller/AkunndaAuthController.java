package org.akuunda.akuundawallet.keycloak.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dto.AuthenticationRequest;
import org.akuunda.akuundawallet.keycloak.api.dto.AuthenticationResponse;
import org.akuunda.akuundawallet.keycloak.api.service.AuthenticationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkunndaAuthController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akunnda - Authentification", description = "Gestion de l'authentification :  permet de generer le token pour s'authentifier")
@RequiredArgsConstructor
public class AkunndaAuthController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/auth/{realmName}";
    private final AuthenticationService authenticationService;


    // ------------------------------------------------------------------------
// LOGIN
// ------------------------------------------------------------------------
    @PostMapping("/generate-token")
    @Operation(
            operationId = "generateToken",
            summary = "Generer un token pour s'authentifier via les apis",
            description = "Permet à un utilisateur de generer un token pour s'authentifier et consommer les apis.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "AuthenticationRequest JSON",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthenticationRequest.class),
                            examples = @ExampleObject(
                                    name = "Authentication Example",
                                    value = """
                                {
                                  "username": "xxxxxxxxxx",
                                  "password": "xxxxxxxxxx",
                                  "grant_type": "xxxxxx",
                                  "client_secret": "xxxxxxxxxx",
                                  "client_id": "xxxxxxxx"
                                }
                                """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion réussie"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Nom d’utilisateur ou mot de passe introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<AuthenticationResponse> generateToken(@PathVariable String realmName,
                                                                @RequestBody @Valid AuthenticationRequest loginRequest) {
        log.info("generate token in realm {}", realmName);
        log.debug("generate token in realm {}", realmName);
        return authenticationService.generateToken(loginRequest, realmName);
    }

}
