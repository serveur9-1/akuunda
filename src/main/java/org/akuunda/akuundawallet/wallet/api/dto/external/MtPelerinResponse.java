package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MtPelerinResponse {

    private String userName;
    private String mtpHash;
    private String mtpCode;
    private String walletAddress;
}
