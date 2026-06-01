package org.akuunda.akuundawallet.wallet.api.requests;

import lombok.Data;

@Data
public class OnrampMoneyRequest {
    private Float fiatAmount;
    private String username;
    private String phoneNumber;
    private String currency;
    private String destinataireName;

}
