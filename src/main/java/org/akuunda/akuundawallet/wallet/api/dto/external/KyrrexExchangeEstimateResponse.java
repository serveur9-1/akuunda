package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Estimation d'échange Kyrrex (swap)")
public class KyrrexExchangeEstimateResponse {

    @Schema(description = "Montant de sortie")
    private String outputAmount;

    @Schema(description = "Montant d'entrée")
    private String inputAmount;

    @Schema(description = "Asset d'entrée")
    private String inputAsset;

    @Schema(description = "Asset de sortie")
    private String outputAsset;

    @Schema(description = "Taux de change")
    private String rate;

    @Schema(description = "Frais")
    private String fee;
}
