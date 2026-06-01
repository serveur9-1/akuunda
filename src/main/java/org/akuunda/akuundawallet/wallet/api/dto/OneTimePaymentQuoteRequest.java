package org.akuunda.akuundawallet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OneTimePaymentQuoteRequest {

    @Schema(description = "Code devise fiat source", example = "EUR")
    private String sourceCurrencyCode;

    @Schema(description = "Code crypto destination", example = "USDC_POLYGON")
    private String destinationCurrencyCode;

    @Schema(description = "Montant en fiat", example = "100")
    private String sourceAmount;

    @Schema(description = "Code pays", example = "FR")
    private String countryCode;
}
