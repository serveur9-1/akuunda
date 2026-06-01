package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.CountryDto;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
public interface CountryService {

    // get All country information except the currency code
    ResponseEntity<List<CountryDto>> getAllCountries();
}
