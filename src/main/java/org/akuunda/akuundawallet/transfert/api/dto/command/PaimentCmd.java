package org.akuunda.akuundawallet.transfert.api.dto.command;

import lombok.Data;

@Data
public class PaimentCmd {
    private String username;
    private String accountNumber;
    private String accountType;
    private String networkId;
    private String chanelId;
    private String country;
    private String currency;
    private double amount;
    private String type; // ONRAMP ou OFFRAMP
    private String reason; // Optionnel pour OFFRAMP

}
