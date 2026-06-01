package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.Data;

@Data
public class SignatureRequest {

    private String type;
    private String secretType;
    private String walletId;
    private String data;
}
