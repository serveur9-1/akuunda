package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WalletCreateRequestDto {

    private String pinecode;
    private String description;
    private String identifier;
    private String secretType;
    private String walletType;
}
