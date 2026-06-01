package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationRequest;
import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationResponse;
import org.akuunda.akuundawallet.wallet.service.FeesCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/fees")
@Slf4j
@Validated
@Tag(name = "Akuunda Services - Fees Calculation", description = "Calcul des frais pour les opérations de dépôt (OnRamp) et de retrait (OffRamp)")
public class FeesCalculationController {

    private final FeesCalculationService feesCalculationService;

    public FeesCalculationController(FeesCalculationService feesCalculationService) {
        this.feesCalculationService = feesCalculationService;
    }

    @PostMapping("/calculate")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "calculateFees",
            summary = "Calculer les frais estimés pour une opération OnRamp (dépôt)",
            description = """
                    Calcule les frais qu'un utilisateur devra payer pour une opération de dépôt (OnRamp).
                    
                    **Pour YellowCard (Afrique):**
                    - L'utilisateur saisit un montant en monnaie locale (ex: 2005 XOF)
                    - Le backend interroge l'API Currency Freaks pour convertir ce montant en USD, selon le taux réel du marché
                    - Le montant converti en USD est ensuite multiplié par le taux du jour de Yellow Card pour obtenir sa valeur équivalente en XOF
                    - Ce montant total est multiplié par le taux de frais Akuunda Pay (2,18%)
                    - Le résultat permet d'afficher à l'utilisateur une estimation des frais ainsi que le montant net qu'il recevra après déduction
                    - Formule: Frais estimés (XOF) = (Montant XOF → USD via Currency Freaks → XOF via YellowCard) × 0.0218
                    - Formule: Montant reçu = Montant initial - Frais estimés
                    - Exemple: 2005 XOF → 3.40 USD (Currency Freaks) → 2002.46 XOF (YellowCard) → Frais: 43.65 XOF (2,18%) → Reçu: 1961.35 XOF
                    
                    **Pour Guardarian (Europe/International):**
                    - Les frais sont déjà calculés par Guardarian dans leur endpoint /estimate
                    - Le montant reçu = montant saisi - frais Guardarian
                    
                    **Réponse:**
                    - `amountSent`: Montant que l'utilisateur paie (montant saisi)
                    - `estimatedFees`: Frais estimés qui seront prélevés (les seuls frais appliqués)
                    - `amountReceived`: Montant que l'utilisateur recevra après déduction des frais
                    
                    **Détection automatique :**
                    - Si l'opérateur n'est pas spécifié, il est détecté automatiquement selon le pays
                    - Pays africains → YellowCard
                    - Autres pays → Guardarian
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête pour calculer les frais",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FeesCalculationRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Exemple YellowCard (Afrique)",
                                            value = """
                                                    {
                                                      "amount": 2005.0,
                                                      "currency": "XOF",
                                                      "countryCode": "CI",
                                                      "operator": "yellowcard"
                                                    }
                                                    """,
                                            description = "Exemple: L'utilisateur dépose 2005 XOF. Conversion XOF→USD via Currency Freaks, puis USD→XOF via YellowCard. Frais estimés ≈ 44 XOF (2,18%), montant reçu ≈ 1961 XOF."
                                    ),
                                    @ExampleObject(
                                            name = "Exemple Guardarian (Europe)",
                                            value = """
                                                    {
                                                      "amount": 30.0,
                                                      "currency": "EUR",
                                                      "countryCode": "FR",
                                                      "operator": "guardarian"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Exemple avec détection automatique",
                                            value = """
                                                    {
                                                      "amount": 5000.0,
                                                      "currency": "XOF",
                                                      "countryCode": "CI"
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Frais calculés avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FeesCalculationResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Réponse YellowCard",
                                            value = """
                                                    {
                                                      "amountSent": 2005.0,
                                                      "currency": "XOF",
                                                      "estimatedFees": 43.65,
                                                      "amountReceived": 1961.35,
                                                      "exchangeRate": 588.96,
                                                      "feePercentage": 2.18,
                                                      "operator": "yellowcard",
                                                      "breakdown": {
                                                        "yellowCardRate": 588.96,
                                                        "akuundaFeeRate": 0.0218,
                                                        "amountInUsd": 3.40,
                                                        "amountAfterYellowCardRate": 2002.46
                                                      }
                                                    }
                                                    """,
                                            description = "Exemple de réponse: Montant déposé 2005 XOF, converti en 3.40 USD (Currency Freaks), puis en 2002.46 XOF (YellowCard), frais 43.65 XOF (2,18%), montant reçu 1961.35 XOF."
                                    ),
                                    @ExampleObject(
                                            name = "Réponse Guardarian",
                                            value = """
                                                    {
                                                      "amountSent": 30.0,
                                                      "currency": "EUR",
                                                      "estimatedFees": 0.15,
                                                      "amountReceived": 29.85,
                                                      "exchangeRate": 1.12524851,
                                                      "feePercentage": 0.5,
                                                      "operator": "guardarian",
                                                      "breakdown": {
                                                        "guardarianExchangeRate": 1.12524851,
                                                        "guardarianConvertedAmount": 29.85,
                                                        "guardarianServiceFee": 0.15
                                                      }
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (montant négatif, paramètres manquants)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "amount": null,
                                              "currency": "XOF"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur (erreur API Currency Freaks, YellowCard ou Guardarian)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "amount": 5000.0,
                                              "currency": "XOF"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<FeesCalculationResponse> calculateFees(@RequestBody @Valid FeesCalculationRequest request) {
        log.info("Calculating fees for amount: {} {}, country: {}, operator: {}", 
                request.getAmount(), request.getCurrency(), request.getCountryCode(), request.getOperator());
        return feesCalculationService.calculateFees(request);
    }

    @PostMapping("/calculate-offramp")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            operationId = "calculateOffRampFees",
            summary = "Calculer les frais estimés pour une opération OffRamp (retrait)",
            description = """
                    Calcule les frais qu'un utilisateur devra payer pour une opération de retrait (OffRamp).
                    
                    **Pour YellowCard (Afrique):**
                    - L'utilisateur saisit un montant en XOF (ce qu'il veut retirer)
                    - Le backend interroge l'API Rate Yellowcard en ciblant le taux "sell" pour convertir ce montant en USD
                    - Le montant converti en USD est ensuite multiplié par le taux "sell" du jour de Yellow Card
                    - Les frais Akuunda (3.5%) sont calculés sur le montant après conversion
                    - Le montant reçu = montant après conversion - frais
                    - Formule: Frais = (Montant XOF/Taux YC × Taux YC) × 0.035
                    
                    **Pour Guardarian (Europe/International):**
                    - L'utilisateur saisit un montant en crypto (ex: 30 USDC) qu'il veut retirer
                    - Le backend appelle l'API Guardarian `/estimate` avec USDC → EUR (inverse de OnRamp)
                    - Les frais sont déjà calculés par Guardarian (0.5%)
                    - Le montant reçu = montant estimé Guardarian (en EUR)
                    
                    **Réponse:**
                    - `amountSent`: Montant que l'utilisateur veut retirer (montant saisi)
                    - `estimatedFees`: Frais estimés qui seront prélevés
                    - `amountReceived`: Montant que l'utilisateur recevra après déduction des frais
                    
                    **Détection automatique :**
                    - Si l'opérateur n'est pas spécifié, il est détecté automatiquement selon le pays
                    - Pays africains → YellowCard
                    - Autres pays → Guardarian
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête pour calculer les frais OffRamp",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FeesCalculationRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Exemple YellowCard OffRamp",
                                            value = """
                                                    {
                                                      "amount": 2005.0,
                                                      "currency": "XOF",
                                                      "countryCode": "CI",
                                                      "operator": "yellowcard"
                                                    }
                                                    """,
                                            description = "Exemple: L'utilisateur veut retirer 2005 XOF. Les frais seront de 70.18 XOF (3.5%), il recevra 1934.82 XOF."
                                    )
                            }
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Frais calculés avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = FeesCalculationResponse.class),
                            examples = @ExampleObject(
                                    name = "Réponse YellowCard OffRamp",
                                    value = """
                                            {
                                              "amountSent": 2005.0,
                                              "currency": "XOF",
                                              "estimatedFees": 70.18,
                                              "amountReceived": 1934.82,
                                              "exchangeRate": 566.94,
                                              "feePercentage": 3.5,
                                              "operator": "yellowcard",
                                              "breakdown": {
                                                "yellowCardRate": 566.94,
                                                "akuundaFeeRate": 0.035,
                                                "amountInUsd": 3.54,
                                                "amountAfterYellowCardRate": 2005.0
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide ou opérateur non supporté pour OffRamp",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "amountSent": 2005.0,
                                              "currency": "XOF",
                                              "operator": "guardarian"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur (erreur API YellowCard)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "amountSent": 2005.0,
                                              "currency": "XOF"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<FeesCalculationResponse> calculateOffRampFees(@RequestBody @Valid FeesCalculationRequest request) {
        log.info("Calculating OffRamp fees for amount: {} {}, country: {}, operator: {}", 
                request.getAmount(), request.getCurrency(), request.getCountryCode(), request.getOperator());
        return feesCalculationService.calculateOffRampFees(request);
    }
}

