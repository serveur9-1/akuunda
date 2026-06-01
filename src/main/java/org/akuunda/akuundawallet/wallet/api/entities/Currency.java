package org.akuunda.akuundawallet.wallet.api.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Currency {

    private String flag;
    private String name;
    private String symbol;
    private boolean onramp;
    private boolean offramp;
    private int fiatType;
}
