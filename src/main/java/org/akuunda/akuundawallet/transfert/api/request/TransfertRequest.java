package org.akuunda.akuundawallet.transfert.api.request;

import lombok.Data;

@Data
public class TransfertRequest {

    private String senderUsername;
    private String receiverUsername;
    private Double amount;
    private Double gasAmount;
    private String signingPin;
    private String devise;
}
