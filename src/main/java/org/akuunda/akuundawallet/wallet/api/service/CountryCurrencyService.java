package org.akuunda.akuundawallet.wallet.api.service;

import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Validated
public interface CountryCurrencyService {

        /**
         * {@inheritDoc}
         */
        CountryCurrency findCountryCurrencyByCountryCode(String countryCode);

        /**
         * {@inheritDoc}
         */
        List<CountryCurrency> findCountryCurrencyByIsActivated();

        /**
         * {@inheritDoc}
         */
        List<CountryCurrency> findCountryCurrencyByContinentName(String continentName);

        /**
         * {@inheritDoc}
         */
        List<CountryCurrency> findCountryCurrencyByCurrencyCode(String currencyCode);

        /**
         * {@inheritDoc}
         */
        CountryCurrency findCountryCurrencyByCallingCode(int callingCode);
}
