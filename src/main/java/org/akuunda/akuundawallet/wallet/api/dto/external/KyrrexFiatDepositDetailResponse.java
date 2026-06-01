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
@Schema(description = "Kyrrex fiat deposit detail response")
public class KyrrexFiatDepositDetailResponse {

    private String uid;

    private String status;

    private BigDecimal amount;

    private String currency;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("provider_id")
    private String providerId;
}
