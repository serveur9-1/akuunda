package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CreateWalletCmd {
    private String pincode;
    private String description;
    private String identifier;
    private String secretType;
    private String walletType;
}
