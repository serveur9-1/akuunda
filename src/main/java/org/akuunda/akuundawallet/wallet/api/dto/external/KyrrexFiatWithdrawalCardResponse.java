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
@Schema(description = "Kyrrex fiat withdrawal card response")
public class KyrrexFiatWithdrawalCardResponse {

    private String uid;

    private String status;

    private BigDecimal amount;

    private String currency;

    @JsonProperty("card_last4")
    private String cardLast4;

    @JsonProperty("created_at")
    private String createdAt;
}
