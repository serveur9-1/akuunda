package org.akuunda.akuundawallet.wallet.service.infrastructure;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.wallet.api.dto.external.WalletBalanceResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.WalletCreateResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.WalletGasBalanceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface AkunndaWalletClientService {

    /**
     * {@inheritDoc}
     */
    ResponseEntity<WalletCreateResponse> getWallet(@Valid final String walletId, @Valid final boolean includeBalance);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<WalletCreateResponse> updateWallet(@Valid final String walletId, @Valid final String body);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<WalletCreateResponse> createWallet(@Valid final String body, @Valid final String signingPinId);

    /**
     * {@inheritDoc}
     */
    boolean validWalletAddress(@Valid final String body);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<WalletBalanceResponse> getWalletTokens(String walletId);

    /**
     * Récupère le solde de gas (MATIC) natif du wallet depuis Venly
     * Utilise l'endpoint /api/wallets/{walletId}/balance (sans /tokens)
     * 
     * @param walletId L'ID du wallet
     * @return ResponseEntity contenant le WalletGasBalanceResponse avec le gasBalance
     */
    ResponseEntity<WalletGasBalanceResponse> getWalletGasBalance(String walletId);

}
