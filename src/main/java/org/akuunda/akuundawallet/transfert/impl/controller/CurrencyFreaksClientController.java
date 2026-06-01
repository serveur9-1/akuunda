package org.akuunda.akuundawallet.transfert.impl.controller;

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
import org.akuunda.akuundawallet.wallet.api.dto.external.CurrencyConversionResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(
        name = "Akuunda - Currency Conversion",
        description = """
        Endpoints internes pour la conversion de devises à partir du service externe CurrencyFreaks.  
        Permet de convertir un montant d'une devise source vers une devise cible selon les taux de change actuels.
        """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequestMapping(path = CurrencyFreaksClientController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
public class CurrencyFreaksClientController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/currency/convert";

    private final CurrencyFreaksClientService currencyFreaksClientService;

    // ------------------------------------------------------------------------
    // ✅ CONVERSION DE DEVISE
    // ------------------------------------------------------------------------
    @GetMapping("/deviseConvert")
    @Operation(
            operationId = "convertCurrency",
            summary = "Convertir une devise en une autre",
            description = """
                    Convertit un montant d'une devise source (`from`) vers une devise cible (`to`)  
                    en utilisant les taux de change fournis par **CurrencyFreaks API**.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Conversion réussie",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "from": "USD",
                                      "to": "EUR",
                                      "amount": 100.0,
                                      "convertedAmount": 92.15,
                                      "exchangeRate": 0.9215,
                                      "timestamp": "2025-10-25T14:22:10Z"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (ex: paramètres manquants ou incorrects)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "Paramètres invalides : 'from' et 'to' sont obligatoires."
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne lors de la communication avec CurrencyFreaks",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "Impossible de récupérer les taux de change actuels."
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<CurrencyConversionResponse> convertCurrency(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam double amount
    ) {
        log.info("CurrencyFreaksController - Conversion demandée: {} → {} | montant={}", from, to, amount);
        return currencyFreaksClientService.convertCurrency(from, to, amount);
    }
}
