package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Estimation des frais de retrait Kyrrex")
public class KyrrexWithdrawalFeeEstimateResponse {

    @Schema(description = "Montant")
    private String amount;

    @Schema(description = "Frais")
    private String fee;

    @JsonProperty("total_amount")
    @Schema(description = "Montant total (montant + frais)")
    private String totalAmount;
}
