package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class RenewProductRequestDto {
    private String userId;
    private String productId;
    private String msisdn;
    private String orderType;
    private Double amount;
}
