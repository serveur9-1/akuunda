package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pays disponible pour un checkout, avec le provider de paiement associé.
 * Retourné par {@code GET /api/v1/checkout/{code}/countries}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pays acceptant le paiement pour ce checkout, avec le provider à utiliser.")
public class CheckoutCountryOption {

    @Schema(example = "CI")
    private String code;

    @Schema(example = "Côte d'Ivoire")
    private String name;

    @Schema(example = "225")
    private Integer callingCode;

    private String continentName;
    private String flagUrl;

    @Schema(example = "XOF")
    private String currencyCode;

    /**
     * Provider à utiliser pour ce pays : {@code YELLOWCARD} (Afrique)
     * ou {@code MELD} (reste du monde).
     */
    @Schema(example = "YELLOWCARD", allowableValues = {"YELLOWCARD", "MELD"})
    private String provider;
}
