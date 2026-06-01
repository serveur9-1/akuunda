package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse pour une devise (endpoint public)")
public class CurrencyResponseDto {

    @Schema(description = "Code devise ISO (ex: XOF, EUR, USD)", example = "XOF")
    private String currencyCode;

    @Schema(description = "Nom de la devise", example = "Franc CFA")
    private String currencyName;

    @Schema(description = "Code pays associé", example = "CI")
    private String countryCode;

    @Schema(description = "Nom du pays", example = "Côte d'Ivoire")
    private String countryName;
}

