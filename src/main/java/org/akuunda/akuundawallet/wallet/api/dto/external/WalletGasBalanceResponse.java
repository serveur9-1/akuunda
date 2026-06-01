package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletGasBalanceResponse {

    private boolean success;
    private GasBalanceResult result;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GasBalanceResult {
        private boolean available;
        private String secretType;
        private double balance;
        private double gasBalance;
        private String symbol;
        private String gasSymbol;
        private String rawBalance;
        private String rawGasBalance;
        private int decimals;
        private Exchange exchange;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Exchange {
        private double usdPrice;
        private double usdBalanceValue;
    }
}

