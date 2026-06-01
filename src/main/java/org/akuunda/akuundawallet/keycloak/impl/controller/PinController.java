package org.akuunda.akuundawallet.keycloak.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.service.PinService;
import org.akuunda.akuundawallet.wallet.service.PinMigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/pin")
@Slf4j
@Validated
@Tag(name = "Akuunda Services - PIN", description = "Gestion des codes PIN et réinitialisation avec Emergency Code")
public class PinController {

    private final PinService pinService;
    private final PinMigrationService pinMigrationService;

    public PinController(PinService pinService, PinMigrationService pinMigrationService) {
        this.pinService = pinService;
        this.pinMigrationService = pinMigrationService;
    }

    /**
     * Endpoint désactivé : Le PIN est maintenant créé automatiquement lors de l'enregistrement (register) de l'utilisateur.
     * Le PIN est créé chez Venly et stocké en base via storePinSecurelyForExistingUser().
     * 
     * @deprecated Utiliser l'endpoint de création d'utilisateur qui crée automatiquement le PIN
     */
    /*
    @PostMapping(path = "/define", produces = "application/json")
    @Operation(
            operationId = "definePin",
            summary = "Définir un code PIN pour un utilisateur",
            description = "Crée un nouveau PIN pour un utilisateur. Le PIN est stocké de manière sécurisée en base de données locale et créé comme signing method chez Venly."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PIN créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> definePin(@RequestBody @Valid DefinePinRequest request) {
        log.info("Define PIN for userId: {}", request.getUserId());
        return pinService.definePin(request.getUserId(), request.getPincode());
    }
    */

    @PostMapping(path = "/reset", produces = "application/json")
    @Operation(
            operationId = "resetPinWithEmergencyCode",
            summary = "Réinitialiser le PIN avec Emergency Code",
            description = "Permet à un utilisateur ayant oublié son PIN de le réinitialiser en utilisant son Emergency Code comme preuve d'identité. " +
                    "L'utilisateur fournit son username (numéro de téléphone) et les 5 caractères de son Emergency Code qu'il a mémorisés. " +
                    "Le système reconstitue le code complet (25 caractères) avec les 20 caractères générés stockés en base pour vérifier chez Venly. " +
                    "Cet endpoint est utilisé hors session (l'utilisateur n'est pas connecté), donc on utilise le username au lieu du userId.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête pour réinitialiser le PIN avec Emergency Code",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResetPinRequest.class),
                            examples = @ExampleObject(
                                    name = "Exemple de requête",
                                    value = """
                                    {
                                      "username": "002250759146858",
                                      "emergencyCode": "BCD25",
                                      "newPincode": "456789"
                                    }
                                    """
                            )
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "PIN réinitialisé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "PIN reset successfully",
                                      "username": "002250759146858",
                                      "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Emergency Code invalide",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": false,
                                      "error": "Venly error",
                                      "message": "Emergency code verification failed",
                                      "username": "002250759146858",
                                      "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur non trouvé ou PIN signing method non trouvé",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": false,
                                      "error": "No emergency code found",
                                      "message": "No emergency code found for user. Please create an emergency code first via /api/internal/v1/emergency-code/define or during registration.",
                                      "username": "002250759146858",
                                      "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
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
                                      "message": "Error resetting PIN: ...",
                                      "username": "002250759146858"
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<String> resetPin(@RequestBody @Valid ResetPinRequest request) {
        log.info("Reset PIN with emergency code for username: {}", request.getUsername());
        return pinService.resetPinWithEmergencyCode(
                request.getUsername(), 
                request.getEmergencyCode(), 
                request.getNewPincode());
    }

    @PostMapping(path = "/verify", produces = "application/json")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "verifyPin",
            summary = "Vérifier un code PIN pour déverrouiller l'application",
            description = "Vérifie si le PIN fourni est correct pour un utilisateur. " +
                    "Utilisé pour déverrouiller l'application mobile après qu'elle ait été verrouillée. " +
                    "Cet endpoint effectue une vérification locale uniquement - il ne renouvelle pas le token JWT. " +
                    "L'application continue d'utiliser le token JWT existant jusqu'à son expiration. " +
                    "L'utilisateur doit être authentifié (token JWT valide).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête pour vérifier le PIN",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = VerifyPinRequest.class),
                            examples = @ExampleObject(
                                    name = "Exemple de requête",
                                    value = """
                                    {
                                      "username": "002250759146858",
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
                    description = "PIN vérifié avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "PIN verified successfully",
                                      "username": "002250759146858",
                                      "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "PIN incorrect",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": false,
                                      "error": "Invalid PIN",
                                      "message": "The provided PIN is incorrect",
                                      "username": "002250759146858",
                                      "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur non trouvé ou PIN non trouvé",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": false,
                                      "error": "PIN not found",
                                      "message": "No PIN found for user",
                                      "username": "002250759146858",
                                      "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
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
                                      "message": "Error verifying PIN: ...",
                                      "username": "002250759146858"
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<String> verifyPin(@RequestBody @Valid VerifyPinRequest request) {
        log.info("Verify PIN for username: {}", request.getUsername());
        return pinService.verifyPin(request.getUsername(), request.getPincode());
    }

    @PostMapping(path = "/migrate-all", produces = "application/json")
    @Operation(
            operationId = "migrateAllUsersPincode",
            summary = "Migrer tous les pincodes des anciens comptes",
            description = "Cette fonction parcourt tous les utilisateurs et met à jour leur pincode à '111111' " +
                    "dans la base de données (avec le nouveau mécanisme de hash) et dans Venly. " +
                    "Cette migration est nécessaire pour les anciens comptes qui avaient déjà un pincode dans Venly " +
                    "mais qui n'ont pas passé par le nouveau mécanisme de hash. " +
                    "⚠️ ATTENTION : Cette opération modifie le pincode de TOUS les utilisateurs. " +
                    "À utiliser uniquement pour la migration des anciens comptes."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Migration terminée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "Migration terminée",
                                      "totalUsers": 150,
                                      "migrated": 145,
                                      "failed": 5,
                                      "skipped": 0,
                                      "errors": "Échec Venly pour userId xxx: ...; "
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
                                      "error": "Erreur critique: ..."
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<String> migrateAllUsersPincode() {
        log.warn("⚠️ Démarrage de la migration des pincodes pour TOUS les utilisateurs");
        return pinMigrationService.migrateAllUsersPincode();
    }

    /**
     * Classe de requête pour l'endpoint /define (désactivé)
     * @deprecated Endpoint désactivé car le PIN est créé automatiquement lors de l'enregistrement
     */
    /*
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Requête pour définir un code PIN")
    public static class DefinePinRequest {
        @NotBlank
        @Schema(description = "ID utilisateur Venly", example = "12345678-1234-1234-1234-123456789012", required = true)
        private String userId;
        
        @NotBlank
        @Schema(description = "Code PIN à définir", example = "1234", required = true)
        private String pincode;
    }
    */

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Requête pour réinitialiser un PIN avec Emergency Code")
    public static class ResetPinRequest {
        @NotBlank
        @Schema(
                description = "Nom d'utilisateur (numéro de téléphone). Utilisé pour récupérer le userId en base de données locale. " +
                        "Cet endpoint est utilisé hors session, donc l'utilisateur n'a pas son userId, seulement son username.",
                example = "002250759146858",
                required = true
        )
        private String username;
        
        @NotBlank
        @Schema(
                description = "Les 5 caractères de l'emergency code que l'utilisateur a mémorisés. " +
                        "Les 20 caractères générés seront récupérés depuis la base de données " +
                        "pour reconstruire le code complet de 25 caractères et vérifier chez Venly.",
                example = "ABC12",
                required = true
        )
        private String emergencyCode;
        
        @NotBlank
        @Schema(description = "Nouveau code PIN (6 caractères)", example = "123456", required = true)
        private String newPincode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Requête pour vérifier un code PIN")
    public static class VerifyPinRequest {
        @NotBlank
        @Schema(
                description = "Nom d'utilisateur (numéro de téléphone). L'utilisateur doit être authentifié (token JWT valide).",
                example = "002250759146858",
                required = true
        )
        private String username;
        
        @NotBlank
        @Schema(
                description = "Code PIN à vérifier (6 caractères)",
                example = "123456",
                required = true
        )
        private String pincode;
    }
}


