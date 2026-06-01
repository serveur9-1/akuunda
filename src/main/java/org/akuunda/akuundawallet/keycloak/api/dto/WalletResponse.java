package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Builder;

@Builder
public record WalletResponse(
        String status,
        String message,
        WalletBalanceDto data
) {
}
