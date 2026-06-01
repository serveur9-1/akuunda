package org.akuunda.akuundawallet.keycloak.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.service.EmergencyCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping({"/api/internal/v1/emergency-code", "/emergency-code"})
@Slf4j
@Validated
@Tag(name = "Akuunda Services - Emergency Code", description = "Gestion des Emergency Codes pour la récupération de compte")
public class EmergencyCodeController {

    private final EmergencyCodeService emergencyCodeService;

    public EmergencyCodeController(EmergencyCodeService emergencyCodeService) {
        this.emergencyCodeService = emergencyCodeService;
    }

    @PostMapping(path = "/define", produces = "application/json")
    @Operation(
            operationId = "defineEmergencyCode",
            summary = "Définir un Emergency Code pour un utilisateur",
            description = "Crée un Emergency Code pour un utilisateur. Le système génère 20 caractères aléatoires, " +
                    "les chiffre et les stocke en base de données. Ces 20 caractères sont concaténés avec les 5 caractères " +
                    "fournis par le client pour former un code complet de 25 caractères qui est envoyé à Venly. " +
                    "Les 5 caractères du client ne sont JAMAIS stockés en base (mesure de sécurité). " +
                    "Nécessite un PIN existant pour l'authentification auprès de Venly. " +
                    "Utilise le username (numéro de téléphone) pour identifier l'utilisateur.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête pour définir un Emergency Code",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DefineEmergencyCodeRequest.class),
                            examples = @ExampleObject(
                                    name = "Exemple de requête",
                                    value = """
                                    {
                                      "username": "002250759146858",
                                      "partialEmergencyCode": "BCD25",
                                      "pincode": "123456"
                                    }
                                    """
                            )
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Emergency Code créé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "Emergency code created successfully",
                                      "username": "002250759146858",
                                      "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": false,
                                      "error": "Invalid PIN",
                                      "message": "PIN code cannot be null or empty",
                                      "username": "002250759146858"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur non trouvé ou PIN signing method non trouvé (créer un PIN d'abord)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": false,
                                      "error": "User not found",
                                      "message": "User with username '002250759146858' not found in database",
                                      "username": "002250759146858"
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
                                      "success": false,
                                      "error": "Internal error",
                                      "message": "Error defining emergency code: ...",
                                      "username": "002250759146858"
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<String> defineEmergencyCode(@RequestBody @Valid DefineEmergencyCodeRequest request) {
        log.info("Define emergency code for username: {}", request.getUsername());
        return emergencyCodeService.defineEmergencyCode(
                request.getUsername(), 
                request.getPartialEmergencyCode(),
                request.getPincode());
    }

    @PostMapping(path = "/change", produces = "application/json")
    @Operation(
            operationId = "changeEmergencyCode",
            summary = "Changer son Emergency Code (code actuel connu)",
            description = "Permet à un utilisateur de changer son Emergency Code en fournissant son code actuel. " +
                    "Le code actuel sert d'authentification auprès de Venly. " +
                    "Un nouveau prefix de 20 caractères est généré, seul le prefix chiffré est mis à jour en base. " +
                    "Les 5 caractères partiels ne sont jamais stockés."
    )
    public ResponseEntity<String> changeEmergencyCode(@RequestBody @Valid ChangeEmergencyCodeRequest request) {
        log.info("Change emergency code for username: {}", request.getUsername());
        return emergencyCodeService.changeEmergencyCode(
                request.getUsername(),
                request.getCurrentPartialEmergencyCode(),
                request.getNewPartialEmergencyCode());
    }

    @GetMapping(path = "/migration-status", produces = "application/json")
    @Operation(
            operationId = "getEmergencyCodeMigrationStatus",
            summary = "Statut de migration du code de secours",
            description = "Règle du 20/05/2026 : requiresPhoneFormatUpdate=false si compte créé "
                    + "ou code de secours modifié à partir de cette date ; true pour les comptes "
                    + "antérieurs dont le code n'a pas été mis à jour depuis."
    )
    public ResponseEntity<String> getEmergencyCodeMigrationStatus(
            @RequestParam @NotBlank String username) {
        log.info("Migration status for username: {}", username);
        boolean userFound = emergencyCodeService.isMigrationStatusUserFound(username);
        boolean hasEmergencyCode = emergencyCodeService.hasEmergencyCodeConfigured(username);
        boolean usesPhoneLast5 = emergencyCodeService.isUsesPhoneLast5FlagSet(username);
        boolean requiresUpdate =
                emergencyCodeService.requiresPhoneFormatEmergencyCodeUpdate(username);
        log.info(
                "Migration status result for {}: userFound={}, hasEmergencyCode={}, usesPhoneLast5={}, requiresUpdate={}",
                username,
                userFound,
                hasEmergencyCode,
                usesPhoneLast5,
                requiresUpdate);
        return ResponseEntity.ok(String.format(
                "{\"success\":true,\"userFound\":%s,\"hasEmergencyCode\":%s,\"usesPhoneLast5\":%s,\"requiresPhoneFormatUpdate\":%s}",
                userFound,
                hasEmergencyCode,
                usesPhoneLast5,
                requiresUpdate));
    }

    @PostMapping(path = "/acknowledge-phone-format", produces = "application/json")
    @Operation(
            operationId = "acknowledgePhoneFormatEmergencyCode",
            summary = "Confirmer un code déjà égal aux 5 derniers chiffres du téléphone",
            description = "Pour les comptes dont l'ancien code de secours est déjà les 5 derniers "
                    + "chiffres du mobile : met à jour uses_phone_last5 sans appel Venly change."
    )
    public ResponseEntity<String> acknowledgePhoneFormatEmergencyCode(
            @RequestParam @NotBlank String username) {
        log.info("Acknowledge phone-format emergency code for username: {}", username);
        return emergencyCodeService.acknowledgePhoneFormatEmergencyCode(username);
    }

    @PostMapping(path = "/verify-partial", produces = "application/json")
    @Operation(
            operationId = "verifyPartialEmergencyCode",
            summary = "Vérifier les 5 caractères partiels du code de secours",
            description = "Permet de vérifier si les 5 caractères fournis correspondent au code de secours enregistré pour l'utilisateur."
    )
    public ResponseEntity<String> verifyPartialEmergencyCode(
            @RequestBody @Valid VerifyPartialEmergencyCodeRequest request) {
        log.info("Verify partial emergency code for username: {}", request.getUsername());
        boolean matches = emergencyCodeService.verifyPartialEmergencyCode(
                request.getUsername(),
                request.getPartialEmergencyCode());
        return ResponseEntity.ok(String.format("{\"success\":true,\"matches\":%s}", matches));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Requête pour vérifier les 5 caractères partiels du code de secours")
    public static class VerifyPartialEmergencyCodeRequest {
        @NotBlank
        private String username;

        @NotBlank
        @Schema(description = "Les 5 caractères partiels à vérifier", example = "45858")
        private String partialEmergencyCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Requête pour changer un Emergency Code existant")
    public static class ChangeEmergencyCodeRequest {
        @NotBlank
        private String username;

        @NotBlank
        @Schema(description = "Les 5 caractères actuels connus de l'utilisateur", example = "12345")
        private String currentPartialEmergencyCode;

        @NotBlank
        @Schema(description = "Les 5 nouveaux caractères choisis par l'utilisateur", example = "67890")
        private String newPartialEmergencyCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Requête pour définir un Emergency Code")
    public static class DefineEmergencyCodeRequest {
        @NotBlank
        @Schema(
                description = "Nom d'utilisateur (numéro de téléphone). Utilisé pour récupérer le userId en base de données locale.",
                example = "002250759146858",
                required = true
        )
        private String username;
        
        @NotBlank
        @Schema(
                description = "Les 5 caractères que le client choisit pour son Emergency Code. " +
                        "Le système génère 20 caractères supplémentaires qui seront concaténés " +
                        "avec ces 5 caractères pour former un code complet de 25 caractères envoyé à Venly. " +
                        "Seuls les 20 caractères générés sont stockés en base (chiffrés), " +
                        "les 5 caractères du client ne sont JAMAIS stockés (mesure de sécurité).",
                example = "ABC12",
                required = true
        )
        private String partialEmergencyCode;
        
        @NotBlank
        @Schema(
                description = "Code PIN de l'utilisateur (6 caractères, nécessaire pour authentifier la création de l'emergency code chez Venly). " +
                        "Le PIN doit avoir été créé au préalable.",
                example = "123456",
                required = true
        )
        private String pincode;
    }
}
