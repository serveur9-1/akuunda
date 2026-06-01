package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.keycloak.api.dto.OtpRequest;
import org.akuunda.akuundawallet.keycloak.api.service.OtpService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = OtpController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda Services - OTP",
        description = """
        Endpoint interne pour la vérification des codes OTP (One Time Password) 
        associés aux comptes utilisateurs Akuunda Wallet.
        """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class OtpController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/otp";

    private final OtpService otpService;

    // ------------------------------------------------------------------------
    // ✅ VERIFY OTP CODE
    // ------------------------------------------------------------------------
    @PostMapping("/verifyOtpCode")
    @Operation(
            operationId = "verifyOtpCode",
            summary = "Vérifier un code OTP",
            description = """
                    Permet de vérifier si le code OTP soumis par l’utilisateur est valide 
                    pour son nom d’utilisateur (username) spécifié.  
                    
                    Ce endpoint est typiquement utilisé après l’envoi d’un OTP par SMS ou email.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OTP vérifié avec succès (valide ou non)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "OTP Verified Example",
                                    value = """
                                    true
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (données manquantes ou mal formatées)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "timestamp": "2025-10-25T12:34:56Z",
                                      "status": 400,
                                      "error": "Bad Request",
                                      "message": "otpCode must not be null",
                                      "path": "/api/internal/v1/otp/verifyOtpCode"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur introuvable pour le username spécifié",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "timestamp": "2025-10-25T12:35:00Z",
                                      "status": 404,
                                      "error": "Not Found",
                                      "message": "User not found with username: johndoe",
                                      "path": "/api/internal/v1/otp/verifyOtpCode"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "timestamp": "2025-10-25T12:35:30Z",
                                      "status": 500,
                                      "error": "Internal Server Error",
                                      "message": "Unexpected error during OTP verification"
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<Boolean> verifyOtpCode(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Données nécessaires pour la vérification du code OTP",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = OtpRequest.class),
                            examples = @ExampleObject(
                                    name = "VerifyOtpRequest",
                                    value = """
                                    {
                                      "username": "johndoe",
                                      "otpCode": "482913"
                                    }
                                    """
                            )
                    )
            )
            @RequestBody final OtpRequest request) {

        log.info("OtpController - verifyOtpCode for user: {}", request.getUsername());
        log.debug("OTP verification request: {}", request);

        boolean isValid = otpService.verifyOtp(request.getOtpCode(), request.getUsername());
        return ResponseEntity.ok(isValid);
    }
}
