package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MtPelerinOffRampResponse {
    private String redirectUrl;
    private String orderId; // merchant_oid
}

