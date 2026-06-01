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
import org.akuunda.akuundawallet.wallet.api.dto.CountryDto;
import org.akuunda.akuundawallet.wallet.service.CountryService;
import org.akuunda.akuundawallet.wallet.service.CountrySynchronizationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = CountryController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda Services - Country",
        description = """
                Endpoints pour la gestion des pays dans Akuunda Wallet.
                
                - **getActivatedCountries** : Liste tous les pays activés dans le système
                - **sync** : Synchronise tous les pays depuis l'API REST Countries (met à jour les pays existants et ajoute les nouveaux)
                """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class CountryController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/country";

    private final CountryService countryService;
    private final CountrySynchronizationService countrySynchronizationService;

    // ------------------------------------------------------------------------
    // GET ACTIVATED COUNTRIES
    // ------------------------------------------------------------------------
    @GetMapping(path = "/getActivatedCountries", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getActivatedCountries",
            summary = "Lister les pays activés",
            description = """
                    Cet endpoint renvoie la liste de tous les pays activés dans la plateforme Akuunda Wallet.  
                    Chaque pays inclut son code ISO, son nom, sa devise et un indicateur d’activation.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Liste des pays activés retournée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CountryDto.class),
                            examples = @ExampleObject(
                                    name = "ActivatedCountries Example",
                                    value = """
                                    [
                                      {
                                        "id": 1,
                                        "name": "Côte d'Ivoire",
                                        "isoCode": "CI",
                                        "currency": "XOF",
                                        "isActive": true
                                      },
                                      {
                                        "id": 2,
                                        "name": "Sénégal",
                                        "isoCode": "SN",
                                        "currency": "XOF",
                                        "isActive": true
                                      }
                                    ]
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
                                    name = "Error Example",
                                    value = """
                                    {
                                      "timestamp": "2025-10-25T14:12:37Z",
                                      "status": 500,
                                      "error": "Internal Server Error",
                                      "message": "Erreur lors de la récupération des pays activés",
                                      "path": "/api/internal/v1/country/getActivatedCountries"
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<List<CountryDto>> getActivatedCountries() {
        log.info("CountryController - retrieving list of activated countries");
        log.debug("Calling CountryService.getAllCountries()");
        return countryService.getAllCountries();
    }

    // ------------------------------------------------------------------------
    // SYNCHRONIZE ALL COUNTRIES FROM REST COUNTRIES API
    // ------------------------------------------------------------------------
    @PostMapping(path = "/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "synchronizeAllCountries",
            summary = "Synchroniser tous les pays depuis l'API REST Countries",
            description = """
                    Cet endpoint synchronise tous les pays depuis l'API REST Countries (https://restcountries.com/v3.1/all).
                    
                    **Fonctionnement :**
                    - Récupère tous les pays disponibles depuis l'API REST Countries (250+ pays)
                    - Vérifie chaque pays dans la base de données
                    - **Met à jour les pays existants** avec les dernières données de l'API (nom, devise, capitale, etc.)
                    - **Les IDs des pays existants sont préservés** (seules les données sont mises à jour)
                    - Crée automatiquement les nouveaux pays manquants
                    - Active automatiquement les nouveaux pays créés
                    
                    **Priorité pour le nom du pays :**
                    - Nom commun en français (`nativeName.fra.common`) si disponible
                    - Sinon nom commun en anglais (`name.common`)
                    - Sinon nom officiel en français (`nativeName.fra.official`)
                    - Sinon nom officiel en anglais (`name.official`)
                    
                    **Résultat retourné :**
                    - `totalCountries` : Nombre total de pays traités depuis l'API
                    - `created` : Nombre de nouveaux pays créés
                    - `updated` : Nombre de pays existants mis à jour avec les nouvelles données
                    - `errors` : Nombre d'erreurs rencontrées
                    - `errorMessages` : Liste des messages d'erreur (si applicable)
                    
                    **Exemple d'utilisation :**
                    ```
                    POST /api/internal/v1/country/sync
                    Authorization: Bearer {token}
                    ```
                    
                    **Note :** 
                    - Cette opération peut prendre quelques minutes (250+ pays à traiter)
                    - Les IDs des pays existants sont préservés (seules les données sont mises à jour)
                    - Les pays existants et nouveaux sont synchronisés avec les dernières données de l'API
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Synchronisation terminée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "SyncResult Example",
                                    value = """
                                    {
                                      "totalCountries": 250,
                                      "created": 29,
                                      "updated": 221,
                                      "errors": 0,
                                      "errorMessages": []
                                    }
                                    """,
                                    summary = "Exemple de réponse réussie"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur lors de la synchronisation",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Error Example",
                                    value = """
                                    {
                                      "totalCountries": 250,
                                      "created": 0,
                                      "updated": 0,
                                      "errors": 1,
                                      "errorMessages": [
                                        "Erreur lors de la récupération des pays depuis l'API"
                                      ]
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<CountrySynchronizationService.CountrySyncResult> synchronizeAllCountries() {
        log.info("🔄 [CountryController] Synchronisation de tous les pays depuis l'API REST Countries...");
        log.debug("Endpoint appelé: POST /api/internal/v1/country/sync");
        try {
            ResponseEntity<CountrySynchronizationService.CountrySyncResult> result = countrySynchronizationService.synchronizeAllCountries();
            log.info("✅ [CountryController] Synchronisation terminée avec succès");
            return result;
        } catch (Exception e) {
            log.error("❌ [CountryController] Erreur lors de la synchronisation: {}", e.getMessage(), e);
            throw e;
        }
    }
}
