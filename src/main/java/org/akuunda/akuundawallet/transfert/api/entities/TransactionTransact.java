package org.akuunda.akuundawallet.transfert.api.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TransactionTransact {
    @Id
    private String orderId;
    private String fiatCurrency;
    private String cryptoCurrency;
    private double fiatAmount;
    private double cryptoAmount;
    private String isBuyOrSell;
    private String status;
    private String walletAddress;
    private double totalFeeInFiat;
    private String partnerCustomerId;
    private String partnerOrderId;
    private String network;
}
