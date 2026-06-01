package org.akuunda.akuundawallet.wallet.api.dto.external;

public record EstimateRequest(
        String from,
        String to,
        double amount
) {}
