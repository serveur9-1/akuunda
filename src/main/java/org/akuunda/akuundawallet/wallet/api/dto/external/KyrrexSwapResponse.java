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
@Schema(description = "Kyrrex swap response")
public class KyrrexSwapResponse {

    private String uid;

    private String status;

    @JsonProperty("input_asset")
    private String inputAsset;

    @JsonProperty("output_asset")
    private String outputAsset;

    @JsonProperty("input_amount")
    private BigDecimal inputAmount;

    @JsonProperty("output_amount")
    private BigDecimal outputAmount;

    private BigDecimal rate;

    @JsonProperty("created_at")
    private String createdAt;
}
