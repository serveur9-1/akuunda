package org.akuunda.akuundawallet.transfert.api.dto.command;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class YellowCardCmd {

    private String chanelId;
    private String currency;
    private String country;
    private Double amount;  // ou localAmount pour On Ramp
    private String reason;
    private String username; // sender
    private String accountNumber; //destination
    private String accountType; //destination
    private String networkId; //destination
    private String type; //ONRAMP ou OFFRAMP
}
