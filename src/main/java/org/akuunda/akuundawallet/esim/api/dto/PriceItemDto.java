package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class PriceItemDto {

    private String currency;
    private String unit;
    private int amount;
}
