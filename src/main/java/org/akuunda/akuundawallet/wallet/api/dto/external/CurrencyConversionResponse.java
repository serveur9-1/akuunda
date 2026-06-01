package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.Data;

@Data
public class CurrencyConversionResponse {
    private String date;
    private String from;
    private String to;
    private String rate;
    private String givenAmount;
    private String convertedAmount;
}
