package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettlementInfoDto {
    private String cryptoCurrency;
    private String cryptoNetwork;
    private double cryptoAmount;
    private double cryptoUSDRate;
    private double cryptoLocalRate;
    @JsonIgnore
    private String expiresAt;
    private String walletAddress;
}
