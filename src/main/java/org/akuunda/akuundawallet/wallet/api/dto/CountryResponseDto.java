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
@Schema(description = "Réponse pour un pays (endpoint public)")
public class CountryResponseDto {

    @Schema(description = "ID du pays", example = "1")
    private Integer id;

    @Schema(description = "Code pays ISO (ex: CI, SN, FR)", example = "CI")
    private String countryCode;

    @Schema(description = "Nom du pays", example = "Côte d'Ivoire")
    private String countryName;

    @Schema(description = "Code devise ISO (ex: XOF, EUR)", example = "XOF")
    private String currencyCode;

    @Schema(description = "Indicateur d'appel téléphonique", example = "225")
    private Integer callingCode;

    @Schema(description = "Capitale du pays", example = "Abidjan")
    private String capital;

    @Schema(description = "Nom du continent", example = "Afrique")
    private String continentName;
}

