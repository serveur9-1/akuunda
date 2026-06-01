package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class PurchaseFlowResponseDto {

    private String subscriptionId;
    private String transactionId;
    private String transactionStatus;
    private String simSerial;
    private String esimStatus;
    private String subscriberStatus;
    private boolean activationRequired;
    private String activationMessage;
    private String activationCode;
    private String qrCodeValue;
    private String qrCodeDataUrl;
}
