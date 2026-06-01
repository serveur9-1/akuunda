package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Token {

    private String secretType;
    private String symbol;
    private String tokenAddress;
}
