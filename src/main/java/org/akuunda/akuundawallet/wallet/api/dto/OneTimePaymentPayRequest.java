package org.akuunda.akuundawallet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OneTimePaymentPayRequest {

    @Schema(description = "Fournisseur de paiement (ex: MERCURYO, TRANSAK, UNLIMIT)", example = "MERCURYO")
    private String serviceProvider;

    @Schema(description = "Code devise fiat source", example = "EUR")
    private String sourceCurrencyCode;

    @Schema(description = "Montant en fiat", example = "100")
    private String sourceAmount;

    @Schema(description = "Code pays", example = "FR")
    private String countryCode;
}
