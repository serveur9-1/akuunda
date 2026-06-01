package org.akuunda.akuundawallet.wallet.api.dto.external;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record GuardiaranPaiement(
        String username,
        String transactionId,
        String headerPin,
        String walletAddress,

        Double amount
) {
}
