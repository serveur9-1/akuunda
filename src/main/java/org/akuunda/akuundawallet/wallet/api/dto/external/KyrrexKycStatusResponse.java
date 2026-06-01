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
@Schema(description = "Kyrrex KYC status response")
public class KyrrexKycStatusResponse {

    @JsonProperty("customer_id")
    private String customerId;

    private String status;

    private String level;

    @JsonProperty("verified_at")
    private String verifiedAt;
}
