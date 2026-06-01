package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.CountryCurrencyRepository;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.api.service.CountryCurrencyService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
public class CountryCurrencyServiceImpl implements CountryCurrencyService {

    private final CountryCurrencyRepository countryCurrencyRepository;

    public CountryCurrencyServiceImpl(CountryCurrencyRepository countryCurrencyRepository) {
        this.countryCurrencyRepository = countryCurrencyRepository;
    }

    @Override
    public CountryCurrency findCountryCurrencyByCountryCode(final String countryCode) {
        return countryCurrencyRepository.findCountryCurrencyByCountryCode(countryCode);
    }

    @Override
    public List<CountryCurrency> findCountryCurrencyByIsActivated() {
        return countryCurrencyRepository.findCountryCurrencyByIsActivated(true);
    }

    @Override
    public List<CountryCurrency> findCountryCurrencyByContinentName(String continentName) {
        return countryCurrencyRepository.findCountryCurrencyByContinentName(continentName);
    }

    @Override
    public List<CountryCurrency> findCountryCurrencyByCurrencyCode(String currencyCode) {
        return countryCurrencyRepository.findCountryCurrencyByCurrencyCode(currencyCode);
    }

    @Override
    public CountryCurrency findCountryCurrencyByCallingCode(int callingCode) {
        return countryCurrencyRepository.findCountryCurrencyByCallingCode(callingCode);
    }
}
