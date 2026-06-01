package org.akuunda.akuundawallet.wallet.api.dto.external;

public record CurrencyResponse(
        String code,
        String name,
        boolean enabled
) {}
