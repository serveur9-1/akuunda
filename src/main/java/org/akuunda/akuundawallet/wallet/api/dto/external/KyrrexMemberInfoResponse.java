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
@Schema(description = "Kyrrex member info response")
public class KyrrexMemberInfoResponse {

    private String uid;

    private String email;

    @JsonProperty("kyc_status")
    private String kycStatus;

    @JsonProperty("kyb_status")
    private String kybStatus;

    private String status;
}
