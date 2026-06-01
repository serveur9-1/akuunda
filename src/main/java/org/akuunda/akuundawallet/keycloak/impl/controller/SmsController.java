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
import org.akuunda.akuundawallet.keycloak.api.dto.SmsRequest;
import org.akuunda.akuundawallet.keycloak.api.service.SmsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
@RequestMapping(path = SmsController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - SMS", description = "Endpoints pour l’envoi de SMS via le service Keycloak intégré")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
public class SmsController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/sms";

    private final SmsService smsService;

    // ------------------------------------------------------------------------
    // ✅ Envoi de SMS
    // ------------------------------------------------------------------------
    @PostMapping("/sendSms")
    @Operation(
            operationId = "sendSms",
            summary = "Envoyer un SMS à un destinataire",
            description = """
                    Permet d’envoyer un SMS à un utilisateur en précisant le message, 
                    le numéro de téléphone destinataire, et le type d’envoi (OTP, NOTIFICATION, etc.).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SMS envoyé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "SUCCESS",
                                      "message": "SMS envoyé avec succès à +2250707070707"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (message ou destinataire manquant)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "Le champ 'message' ou 'destinataire' est manquant."
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur lors de l’envoi du SMS",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "Impossible d’envoyer le SMS au destinataire."
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<String> sendSms(@RequestBody SmsRequest request) {
        log.info("SMSController - Envoi de message: '{}' au destinataire: '{}' (type={})",
                request.getMessage(), request.getDestinataire(), request.getType());
        log.debug("Détails de la requête SMS: {}", request);

        return smsService.sendSms(request.getMessage(), List.of(request.getDestinataire()), request.getType());
    }
}
