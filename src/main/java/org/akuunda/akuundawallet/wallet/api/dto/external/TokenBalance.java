package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenBalance {

    private String tokenAddress;
    private String rawBalance;
    private double balance;
    private int decimals;
    private String symbol;
    private String logo;
    private String type;
    private boolean transferable;
    private String name;
    private Exchange exchange;
    private List<String> categories;
    private Map<String, String> links;
    private String thumbnail;
    private String portfolioPercentage;
}
