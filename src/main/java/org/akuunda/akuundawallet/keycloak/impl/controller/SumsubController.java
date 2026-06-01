package org.akuunda.akuundawallet.keycloak.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.keycloak.api.dto.external.sumsub.AccessToken;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.SumsubClientService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
@RequestMapping(path = SumsubController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Sumsub", description = "Endpoints pour l’intégration avec Sumsub (vérification d’identité KYC)")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
public class SumsubController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/sumsub";

    private final SumsubClientService sumsubService;

    // ------------------------------------------------------------------------
    // ✅ Récupération du token d’accès Sumsub
    // ------------------------------------------------------------------------
    @GetMapping("/accessToken")
    @Operation(
            operationId = "getAccessToken",
            summary = "Obtenir un token d’accès Sumsub",
            description = """
                    Retourne un jeton d’accès temporaire permettant d’initialiser 
                    une session KYC avec **Sumsub** pour un utilisateur donné.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Token Sumsub généré avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                                      "ttlInSecs": 600,
                                      "userId": "akuunda_user_123"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (paramètres manquants ou incorrects)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "Le paramètre 'username' est obligatoire."
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur ou de communication avec Sumsub",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "Impossible de générer le token Sumsub pour cet utilisateur."
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<AccessToken> getAccessToken(
            @RequestParam String username,
            @RequestParam String levelName
    ) {
        log.info("SumsubController - Génération du token d’accès pour user='{}', level='{}'", username, levelName);
        log.debug("Détails de la requête Sumsub - username={}, levelName={}", username, levelName);

        return sumsubService.getAccessToken(username, levelName);
    }
}
