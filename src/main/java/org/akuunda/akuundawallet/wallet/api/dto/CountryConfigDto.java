package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CountryConfigDto {
    private String status;
    private String message;
    private DataDto data;

    @Data
    public static class DataDto {
        private Map<String, CountryDto> buy;
        private Map<String, CountryDto> sell;
    }

    @Data
    public static class CountryDto {
        private String currency;
        private String country;
        private int isActive;
        private Map<String, Integer> paymentMethods;
    }
}
