package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiKycResponse {
    private String userId;
    private String kycUrl;
    private String kycStatus;
    private String token;
    private String message;
}
