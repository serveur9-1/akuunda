package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.*;

@Data
@Builder
@Setter
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Balance {

    private boolean available;
    private String secretType;
    private int balance;
    private int gasBalance;
    private String symbol;
    private String gasSymbol;
    private String rawBalance;
    private String rawGasBalance;
    private int decimals;

}
