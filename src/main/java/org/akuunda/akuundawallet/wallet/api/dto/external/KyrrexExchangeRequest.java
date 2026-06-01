package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Requête d'échange Kyrrex (swap)")
public class KyrrexExchangeRequest {

    @JsonProperty("input_asset")
    @Schema(description = "Asset d'entrée", example = "EUR")
    private String inputAsset;

    @JsonProperty("output_asset")
    @Schema(description = "Asset de sortie", example = "BTC")
    private String outputAsset;

    @Schema(description = "Montant")
    private BigDecimal amount;
}
