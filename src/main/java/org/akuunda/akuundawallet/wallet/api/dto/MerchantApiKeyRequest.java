package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantApiKeyRequest {
    private String webhookUrl;
    private String callbackUrl;
    private String cancelUrl;
}
