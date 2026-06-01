package org.akuunda.akuundawallet.wallet.api.dto.external;

public record MarketResponse(
        String from,
        String to,
        double minAmount,
        double maxAmount,
        double rate
) {}
