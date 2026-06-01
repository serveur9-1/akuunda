package org.akuunda.akuundawallet.wallet.service.controller;


import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.transfert.api.dto.OperationUpdateDto;
import org.akuunda.akuundawallet.wallet.api.dto.OperationDto;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.service.AkuundaOperationService;
import org.akuunda.akuundawallet.wallet.service.OperationMigrationService;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaOperationController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Operations", description = "Endpoints pour la gestion des operations")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class AkuundaOperationController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/operations";

    private final AkuundaOperationService akuundaOperationService;
    private final OperationMigrationService operationMigrationService;

    @io.swagger.v3.oas.annotations.Operation(summary = "Enregistrer une nouvelle opération")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Opération enregistrée avec succès"),
            @ApiResponse(responseCode = "400", description = "Données invalides", content = @Content),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur", content = @Content)
    })
    @PostMapping("/save-operation")
    public ResponseEntity<String> saveOperation(@RequestBody Operation operation) {
        log.info("Requête pour enregistrer une opération : {}", operation);
        log.debug("Requête pour enregistrer une opération : {}", operation);
        return akuundaOperationService.saveOperation(operation);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Récupérer une opération par ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Opération trouvée",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Operation.class))),
            @ApiResponse(responseCode = "404", description = "Opération non trouvée", content = @Content),
            @ApiResponse(responseCode = "400", description = "ID invalide", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<OperationDto> getOperation(@PathVariable Long id) {
        log.info("Requête pour récupérer une opération avec ID : {}", id);
        log.debug("Requête pour récupérer une opération avec ID : {}", id);
        return akuundaOperationService.getOperation(id);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Récupérer toutes les opérations d'un utilisateur")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des opérations récupérée avec succès"),
            @ApiResponse(responseCode = "400", description = "Paramètres invalides", content = @Content)
    })
    @GetMapping("/user-operations")
    public ResponseEntity<Page<OperationDto>> getAllOperationsByUser(@RequestParam String username,
                                                                     @RequestParam String page,
                                                                     @RequestParam String size) {
        log.info("Requête pour récupérer les opérations de l'utilisateur : {}", username);
        log.debug("Requête pour récupérer les opérations de l'utilisateur : {}", username);
        return akuundaOperationService.getAllOperationsByUser(username, page, size);

    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Mettre à jour une opération et modifier le wallet associé")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Opération mise à jour avec succès"),
            @ApiResponse(responseCode = "404", description = "Opération ou utilisateur non trouvé"),
            @ApiResponse(responseCode = "400", description = "Données invalides")
    })
    @PutMapping("/update-operation")
    public ResponseEntity<String> updateOperation(@RequestBody OperationUpdateDto operationUpdateDto) {
        log.info("Mise à jour de l'opération pour l'utilisateur : {}", operationUpdateDto.getUsername());
        return akuundaOperationService.updateOperation(operationUpdateDto);
    }

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Migrer toutes les opérations pour corriger les données incorrectes",
            description = """
                    Migre toutes les opérations en base de données pour corriger les données incorrectes.
                    
                    **Corrections effectuées :**
                    1. **Dates manquantes** : Ajoute `createdAt` et `updatedAt` si absents
                    2. **Opérations Guardarian** :
                       - Récupère les montants (`amount`, `convertedAmount`) depuis `GuardarianTransaction`
                       - Normalise les statuts (ex: "new" → "NEW", "valide" → "VALIDEE")
                       - Corrige `username` et `devise` si manquants
                    3. **Statuts non normalisés** : Convertit tous les statuts vers les valeurs standardisées
                    
                    **Statuts normalisés :**
                    - `NEW` : Nouvelle opération, en attente de traitement
                    - `EN ATTENTE` : Opération en cours de traitement
                    - `VALIDEE` : Opération validée et complétée
                    - `ANNULEE` : Opération annulée
                    - `REJETEE` : Opération rejetée/échouée
                    
                    **Réponse :**
                    Retourne des statistiques détaillées sur les corrections effectuées (texte formaté).
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Migration terminée avec succès - Retourne des statistiques détaillées"),
            @ApiResponse(responseCode = "500", description = "Erreur lors de la migration", content = @Content)
    })
    @PostMapping("/migrate-operations")
    public ResponseEntity<org.akuunda.akuundawallet.wallet.api.dto.MigrationResponseDto> migrateOperations() {
        log.info("🚀 Démarrage de la migration des opérations via endpoint");
        return operationMigrationService.migrateAllOperations();
    }
}
