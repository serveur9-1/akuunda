package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KyrrexSepaOrchestrationRequest {
    private String providerId;
    private String instrument; // SEPA | SEPA_IFRAME
    private String instrumentId;
    private Object instrumentRegistrationBody;
    private String outputAsset; // default USDC
    private BigDecimal exchangeAmount;
}
