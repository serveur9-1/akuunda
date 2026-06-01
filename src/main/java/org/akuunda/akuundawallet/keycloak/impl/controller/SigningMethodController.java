package org.akuunda.akuundawallet.keycloak.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.keycloak.api.dto.SigninRequest;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.keycloak.api.service.SigningMethodService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
@RequestMapping(path = SigningMethodController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Signing Methods", description = "Gestion des méthodes de signature (PIN, biométrie, etc.)")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
public class SigningMethodController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/signing-methods";

    private final SigningMethodService signingMethodService;

    // ------------------------------------------------------------------------
    // ✅ Création d'une méthode de signature
    // ------------------------------------------------------------------------
    @PostMapping("/{realmName}")
    @Operation(
            operationId = "createSigningMethod",
            summary = "Créer une méthode de signature",
            description = "Crée une nouvelle méthode de signature (PIN, biométrie...) pour un utilisateur donné dans un realm spécifique."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Méthode de signature créée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "SUCCESS",
                                      "message": "Signing method created successfully"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (paramètres manquants ou incorrects)",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(mediaType = "application/json")
            )
    })
    public ResponseEntity<String> createSigningMethod(
            @PathVariable String realmName,
            @RequestBody @Valid SigninRequest signinRequest) {

        log.info("Création d'une méthode de signature pour user='{}' dans realm='{}'",
                signinRequest.getUserName(), realmName);
        log.debug("Détails : type={}, value={}, pin={}",
                signinRequest.getMethodType(), signinRequest.getMethodValue(), signinRequest.getSigningPin());

        return signingMethodService.createSigning(
                signinRequest.getMethodValue(),
                signinRequest.getMethodType(),
                signinRequest.getUserName(),
                signinRequest.getSigningPin(),
                signinRequest.getPhysicalDeviceId()
        );
    }

    // ------------------------------------------------------------------------
    // ✅ Récupération des méthodes de signature d'un utilisateur
    // ------------------------------------------------------------------------
    @GetMapping("/{realmName}")
    @Operation(
            operationId = "getAllSigningMethods",
            summary = "Lister les méthodes de signature d’un utilisateur",
            description = "Récupère toutes les méthodes de signature configurées pour un utilisateur donné dans un realm spécifique."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Liste récupérée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    [
                                      {
                                        "methodType": "PIN",
                                        "methodValue": "****",
                                        "active": true
                                      },
                                      {
                                        "methodType": "BIOMETRIC",
                                        "methodValue": "faceId",
                                        "active": false
                                      }
                                    ]
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<ExternalSigningMethod>> getAllSigningMethods(
            @PathVariable String realmName,
            @RequestParam String username) {

        log.info("Récupération des méthodes de signature pour user='{}' dans realm='{}'", username, realmName);
        log.debug("Get all signing methods - username={}, realm={}", username, realmName);

        return signingMethodService.getAllSigningMethods(username);
    }
}
