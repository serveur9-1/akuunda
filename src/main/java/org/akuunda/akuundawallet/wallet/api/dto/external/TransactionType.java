package org.akuunda.akuundawallet.wallet.api.dto.external;

public enum TransactionType {
    ONRAMP,   // Fiat -> Crypto (achat)
    OFFRAMP,  // Crypto -> Fiat (vente)
    SWAP,     // Crypto -> Crypto
    UNKNOWN
}
