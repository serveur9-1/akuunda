package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.CountryCurrencyRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CountryDto;
import org.akuunda.akuundawallet.wallet.service.CountryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CountryServiceImpl implements CountryService {

    private final CountryCurrencyRepository countryCurrencyRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseEntity<List<CountryDto>> getAllCountries() {
        log.info("getAllCountries");
        log.debug("getAllCountries");

        final var countries = countryCurrencyRepository.findCountryCurrencyByIsActivated(true);
        if (countries.isEmpty()) {
            return ResponseEntity.ok(null);
        }

        final var countryDtos = countries.stream()
                .map(country -> new CountryDto(country.getId(), country.getCountryCode(), country.getCountryName(),
                        country.getCallingCode(), country.getCapital(), country.getContinentName()))
                .toList();

        return new ResponseEntity<>(countryDtos, HttpStatus.OK);
    }
}
