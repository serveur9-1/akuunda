package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OffRampResponse {

    private SenderDTO sender;
    private DestinationDTO destination;
    private String channelId;
    private String sequenceId;
    private String currency;
    private String country;
    private String reason;
    private boolean forceAccept;
    private boolean directSettlement;
    private SettlementInfoDto settlementInfo;
    private String partnerId;
    private String requestSource;
    private BigDecimal amount;
    private String id;
    private int attempt;
    private String status;
    private BigDecimal convertedAmount;
    private BigDecimal rate;
    @JsonIgnore
    private String expiresAt;
    private boolean tier0Active;
    private String fiatWallet;
    @JsonIgnore
    private String createdAt;
    @JsonIgnore
    private String updatedAt;
}
