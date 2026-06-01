package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.external.CurrencyConversionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface CurrencyFreaksClientService {
    ResponseEntity<CurrencyConversionResponse> convertCurrency(String from, String to, double amount);
}
