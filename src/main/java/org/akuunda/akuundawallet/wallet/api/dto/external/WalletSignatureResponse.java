package org.akuunda.akuundawallet.wallet.api.dto.external;

public record WalletSignatureResponse(
    String userName,
    boolean signature,
    String hash
) {
}
