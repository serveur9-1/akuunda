package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@ToString
public class OffRampRequest {
    private String channelId;
    private String currency;
    private String country;
    private String reason;
    private double amount;
    private boolean forceAccept;
    private boolean directSettlement;
    private RecipientDto sender;
    private SourceDto destination;
    private SettlementInfoDto settlementInfo;

}
