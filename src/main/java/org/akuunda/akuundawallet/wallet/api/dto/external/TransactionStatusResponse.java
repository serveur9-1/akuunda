package org.akuunda.akuundawallet.wallet.api.dto.external;

public record TransactionStatusResponse(
        String status,
        String username,
        String transactionId
) {
}
