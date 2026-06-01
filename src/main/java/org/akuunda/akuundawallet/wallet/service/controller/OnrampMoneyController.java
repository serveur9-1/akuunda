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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.CountryConfigDto;
import org.akuunda.akuundawallet.wallet.api.dto.OnrampMoneyResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.QuoteRequestDTO;
import org.akuunda.akuundawallet.wallet.api.dto.external.QuotesResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.ResponseQuotes;
import org.akuunda.akuundawallet.wallet.api.requests.OnrampMoneyRequest;
import org.akuunda.akuundawallet.wallet.service.infrastructure.OnrampMoneyServiceClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = OnrampMoneyController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda Services - OnRamp Money",
        description = """
        Cette API gère la communication avec le fournisseur **OnRamp Money**, permettant :
        - La génération de liens de widget pour l'achat de crypto via fiat,
        - La récupération des configurations pays supportés,
        - La vérification de la disponibilité du service,
        - L’obtention de devis (quotes) en temps réel.
        """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class OnrampMoneyController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/onramp-money";

    private final OnrampMoneyServiceClient onrampMoneyServiceClient;

    // ------------------------------------------------------------------------
    // 1️⃣ GÉNÉRER UN LIEN DE WIDGET
    // ------------------------------------------------------------------------
    @PostMapping("/generate-link")
    @Operation(
            operationId = "generateWidgetLink",
            summary = "Générer un lien de widget Onramp Money",
            description = """
                    Permet de générer un lien sécurisé vers le widget **OnRamp Money**, 
                    utilisé pour initier une transaction d’achat de crypto depuis la monnaie fiat locale.
                    
                    **Exemple d'utilisation :**
                    - L’utilisateur sélectionne un montant en fiat (XOF, NGN, etc.)
                    - Le backend Akuunda appelle ce endpoint
                    - On retourne une URL OnRampMoney unique et sécurisée
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lien généré avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OnrampMoneyResponse.class),
                            examples = @ExampleObject(
                                    name = "WidgetLinkResponse",
                                    value = """
                                    {
                                      "status": "success",
                                      "message": "Widget link generated successfully",
                                      "data": {
                                        "link": "https://onramp.money/widget?token=abc123xyz",
                                        "urlHash": "xyz123abc"
                                      }
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
                                    name = "BadRequestResponse",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Invalid request parameters",
                                  "data": null
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
                                    name = "InternalServerErrorResponse",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "An unexpected error occurred",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<OnrampMoneyResponse> generateWidgetLink(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Paramètres nécessaires à la génération du lien Onramp Money",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = OnrampMoneyRequest.class),
                            examples = @ExampleObject(
                                    name = "GenerateLinkRequest",
                                    value = """
                                    {
                                      "countryCode": "NG",
                                      "fiatCurrency": "NGN",
                                      "cryptoCurrency": "USDT",
                                      "amount": 10000,
                                      "walletAddress": "0x123456789abcdef...",
                                      "username": "john_doe",
                                      "phoneNumber": "+2347012345678"
                                    }
                                    """
                            )
                    )
            )
            @RequestBody OnrampMoneyRequest request) {

        log.info("OnRampMoneyController - generateWidgetLink: {}", request);
        return onrampMoneyServiceClient.generateWidgetLink(request);
    }

    // ------------------------------------------------------------------------
    // 2️⃣ CONFIGURATION DES PAYS SUPPORTÉS
    // ------------------------------------------------------------------------
    @GetMapping("/country-config")
    @Operation(
            operationId = "fetchAllCountryConfig",
            summary = "Récupérer la configuration de tous les pays pris en charge",
            description = """
                    Récupère la liste des pays et devises supportés par **OnRamp Money**,
                    avec leurs configurations spécifiques (ex : limites, statuts, devises autorisées).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Configurations récupérées avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "CountryConfigResponseSuccess",
                                    value = """
                                {
                                  "status": "success",
                                  "data": {
                                    "buy": {
                                      "NG": {
                                        "currency": "NGN",
                                        "isActive": 1,
                                        "paymentMethods": ["bank_transfer", "card"]
                                      },
                                      "CI": {
                                        "currency": "XOF",
                                        "isActive": 0,
                                        "paymentMethods": []
                                      }
                                    }
                                  }
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
                                    name = "CountryConfigResponseBadRequest",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Invalid request parameters",
                                  "data": null
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
                                    name = "CountryConfigResponseInternalServerError",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "An unexpected error occurred",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<CountryConfigDto> fetchAllCountryConfig() {
        log.info("OnRampMoneyController - fetchAllCountryConfig");
        return onrampMoneyServiceClient.fetchAllCountryConfig();
    }

    // ------------------------------------------------------------------------
    // 3️⃣ VÉRIFIER DISPONIBILITÉ DU SERVICE DANS UN PAYS
    // ------------------------------------------------------------------------
    @GetMapping("/verify-active/{codePays}")
    @Operation(
            operationId = "verifyIfIsActive",
            summary = "Vérifier si Onramp Money est actif dans un pays donné",
            description = """
                    Vérifie si le service OnRamp Money est **disponible et activé** 
                    pour un pays spécifique basé sur son code ISO Alpha-2.
                    
                    **Exemple :**
                    - `GET /api/internal/v1/onramp-money/verify-active/CM`
                    - Réponse : `{ "active": 1 }`
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "État du service récupéré avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "VerifyActiveResponse",
                                    value = """
                                    {
                                      "status": "success",
                                      "message": "Service availability verified",
                                      "data": {
                                        "active": 1
                                      }
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Pays non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<Map<String, Integer>> verifyIfIsActive(
            @Parameter(description = "Code ISO Alpha-2 du pays (ex: CM, NG, CI)", example = "CM")
            @PathVariable String codePays) {

        log.info("OnRampMoneyController - verifyIfIsActive: {}", codePays);
        return onrampMoneyServiceClient.verifyIfIsActive(codePays);
    }

    // ------------------------------------------------------------------------
    // 4️⃣ RÉCUPÉRER UN QUOTE (DEMANDE DE PRIX)
    // ------------------------------------------------------------------------
    @PostMapping("/quotes")
    @Operation(
            operationId = "getOnrampQuotes",
            summary = "Obtenir un devis (quote) Onramp Money",
            description = """
                    Retourne une estimation de conversion fiat → crypto pour un montant donné.  
                    Utilisé pour informer l’utilisateur avant de procéder à la transaction.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Quote récupérée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResponseQuotes.class),
                            examples = @ExampleObject(
                                    name = "QuoteResponseSuccess",
                                    value = """
                                {
                                  "status": "success",
                                  "message": "Quotes retrieved successfully",
                                  "data": {
                                    "totalFees": 85.0,
                                    "amountWithFees": 10085.0
                                  }
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
                                    name = "QuoteResponseBadRequest",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Bad request: Currency not supported",
                                  "data": null
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
                                    name = "QuoteResponseInternalServerError",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "internal server error: Exception message",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<ResponseQuotes> getOnrampQuotes(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Paramètres nécessaires pour obtenir le quote (montant, devise, crypto, etc.)",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = QuoteRequestDTO.class),
                            examples = @ExampleObject(
                                    name = "QuoteRequest",
                                    value = """
                                    {
                                      "fiatCurrency": "NGN",
                                      "cryptoCurrency": "USDT",
                                      "amount": 10000
                                    }
                                    """
                            )
                    )
            )
            @RequestBody QuoteRequestDTO request) {

        log.info("OnRampMoneyController - getOnrampQuotes: {}", request);
        return onrampMoneyServiceClient.getQuotes(request);
    }
}
