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
@Schema(description = "Kyrrex balance response")
public class KyrrexBalanceResponse {

    private String currency;

    private BigDecimal amount;

    @JsonProperty("available_amount")
    private BigDecimal availableAmount;

    @JsonProperty("locked_amount")
    private BigDecimal lockedAmount;

    private String type;
}
