package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class PricesDto {

    // List<List<PriceItemDto>>
    private List<List<PriceItemDto>> subscriptionFee;
    private List<List<PriceItemDto>> renewalFee;
}
