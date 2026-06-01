package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.api.service.CountryCurrencyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = CountryCurrencyController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda Services - Currency-Country",
        description = """
        API permettant de gérer et consulter les informations sur les pays, devises et continents.  
        Ces endpoints sont utilisés pour déterminer les correspondances entre **pays**, **devise** et **indicateurs régionaux**.
        """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class CountryCurrencyController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/currency";

    private final CountryCurrencyService currencyService;

    // ------------------------------------------------------------------------
    // 1️⃣ GET BY CURRENCY CODE
    // ------------------------------------------------------------------------
    @GetMapping("/getCurrencyByCurrencyCode/{currencyCode}")
    @Operation(
            operationId = "getCurrencyByCurrencyCode",
            summary = "Récupérer la liste des pays associés à un code de devise",
            description = """
                    Retourne la liste des pays utilisant une devise spécifique.
                    
                    **Exemple :**
                    - Requête : `GET /api/internal/v1/currency/getCurrencyByCurrencyCode/XOF`
                    - Réponse : Liste des pays utilisant le franc CFA (XOF)
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Liste des pays récupérée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CountryCurrency.class),
                            examples = @ExampleObject(
                                    name = "CurrencyByCodeExample",
                                    value = """
                                    [
                                      {
                                        "countryName": "Côte d'Ivoire",
                                        "countryCode": "CI",
                                        "currencyCode": "XOF",
                                        "continentName": "Africa",
                                        "callingCode": 225,
                                        "isActivated": true
                                      },
                                      {
                                        "countryName": "Sénégal",
                                        "countryCode": "SN",
                                        "currencyCode": "XOF",
                                        "continentName": "Africa",
                                        "callingCode": 221,
                                        "isActivated": true
                                      }
                                    ]
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Code de devise invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<CountryCurrency>> getCurrencyByCurrencyCode(
            @Parameter(description = "Code de la devise (ex: XOF, USD, EUR)", example = "XOF")
            @PathVariable @Valid String currencyCode) {

        log.info("CurrencyController - getCurrencyByCurrencyCode: {}", currencyCode);
        return ResponseEntity.ok(currencyService.findCountryCurrencyByCurrencyCode(currencyCode));
    }

    // ------------------------------------------------------------------------
    // 2️⃣ GET BY CONTINENT NAME
    // ------------------------------------------------------------------------
    @GetMapping("/getCurrencyByContinentName/{continentName}")
    @Operation(
            operationId = "getCurrencyByContinentName",
            summary = "Récupérer les devises utilisées sur un continent",
            description = """
                    Retourne la liste des devises associées à un continent spécifique.
                    
                    **Exemple :**
                    - Requête : `GET /api/internal/v1/currency/getCurrencyByContinentName/Africa`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste récupérée avec succès"),
            @ApiResponse(responseCode = "400", description = "Nom de continent invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<CountryCurrency>> getCurrencyByContinentName(
            @Parameter(description = "Nom du continent (ex: Africa, Europe, Asia)", example = "Africa")
            @PathVariable @Valid String continentName) {

        log.info("CurrencyController - getCurrencyByContinentName: {}", continentName);
        return ResponseEntity.ok(currencyService.findCountryCurrencyByContinentName(continentName));
    }

    // ------------------------------------------------------------------------
    // 3️⃣ GET ACTIVATED CURRENCIES
    // ------------------------------------------------------------------------
    @GetMapping("/getActivatedCurrency")
    @Operation(
            operationId = "getActivatedCurrency",
            summary = "Lister toutes les devises activées",
            description = "Retourne la liste complète des devises actives dans le système Akuunda."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste récupérée avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<CountryCurrency>> getActivatedCurrency() {
        log.info("CurrencyController - getActivatedCurrency");
        return ResponseEntity.ok(currencyService.findCountryCurrencyByIsActivated());
    }

    // ------------------------------------------------------------------------
    // 4️⃣ GET BY CALLING CODE
    // ------------------------------------------------------------------------
    @GetMapping("/getCurrencyByCallingCode/{callingCode}")
    @Operation(
            operationId = "getCurrencyByCallingCode",
            summary = "Récupérer une devise à partir d’un indicatif téléphonique",
            description = """
                    Permet de déterminer la devise d’un pays à partir de son indicatif téléphonique.
                    
                    **Exemple :**
                    - Requête : `GET /api/internal/v1/currency/getCurrencyByCallingCode/237`
                    - Réponse : Détails du pays et de la devise (Cameroun → XAF)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devise récupérée avec succès"),
            @ApiResponse(responseCode = "400", description = "Indicatif invalide"),
            @ApiResponse(responseCode = "404", description = "Aucune devise trouvée pour cet indicatif"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<CountryCurrency> getCurrencyByCallingCode(
            @Parameter(description = "Indicatif téléphonique du pays (ex: 237 pour Cameroun)", example = "237")
            @PathVariable @Valid int callingCode) {

        log.info("CurrencyController - getCurrencyByCallingCode: {}", callingCode);
        return ResponseEntity.ok(currencyService.findCountryCurrencyByCallingCode(callingCode));
    }

    // ------------------------------------------------------------------------
    // 5️⃣ GET BY COUNTRY CODE
    // ------------------------------------------------------------------------
    @GetMapping("/getCurrencyByCountryCode/{countryCode}")
    @Operation(
            operationId = "getCurrencyByCountryCode",
            summary = "Récupérer la devise d’un pays à partir de son code ISO",
            description = """
                    Retourne la devise associée à un pays donné (basé sur le code ISO Alpha-2).
                    
                    **Exemple :**
                    - Requête : `GET /api/internal/v1/currency/getCurrencyByCountryCode/CM`
                    - Réponse : `XAF`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devise récupérée avec succès"),
            @ApiResponse(responseCode = "400", description = "Code pays invalide"),
            @ApiResponse(responseCode = "404", description = "Aucune devise trouvée pour ce pays"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<CountryCurrency> getCurrencyByCountryCode(
            @Parameter(description = "Code ISO Alpha-2 du pays", example = "CM")
            @PathVariable @Valid String countryCode) {

        log.info("CurrencyController - getCurrencyByCountryCode: {}", countryCode);
        return ResponseEntity.ok(currencyService.findCountryCurrencyByCountryCode(countryCode));
    }
}
