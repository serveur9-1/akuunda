package org.akuunda.akuundawallet.transfert.api.dto.command;

import lombok.Data;

@Data
public class TransactPaymentCmd {

    private String username;
    private String amount;
    private String devise;
    private String operationType;

}
