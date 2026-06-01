package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class SubscribeProductRequestDto {

    private String msisdn;
    private String productId;

    private String paymentProvider;
    private String orderType;
    private String source;
    private String mvnoRef;
    private Boolean skipPayment;
}
