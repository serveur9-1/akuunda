package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class RenewSubscriptionRequestDto {
    private String userId;
    private String msisdn;
    private String orderType;
    private Double amount;
}
