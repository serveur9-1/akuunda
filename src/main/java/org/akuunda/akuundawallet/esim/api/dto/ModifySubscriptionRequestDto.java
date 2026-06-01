package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class ModifySubscriptionRequestDto {

    private String msisdn;
    private String productId;
    private String subscriptionId;
    private String transactionReference;
    private String source;
    private String mvnoRef;
}
