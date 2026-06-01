package org.akuunda.akuundawallet.wallet.api.service;

import org.akuunda.akuundawallet.wallet.api.dto.external.WalletCreateResponse;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface WalletService {

    /**
     * {@inheritDoc}
     */
    ResponseEntity<String> createWallet(final String userName, final String signingPin, final CountryCurrency countryCurrency, String reference);

    /**
     * {@inheritDoc}
     */
    WalletCreateResponse updateWallet(final String userName, final String callingCode);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<String> activateWallet(final String userName, final String signingPin);

    ResponseEntity<String> getWalletBalance(final String userName);
}
