package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.CountryConfigDto;
import org.akuunda.akuundawallet.wallet.api.dto.OnrampMoneyResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.QuoteRequestDTO;
import org.akuunda.akuundawallet.wallet.api.dto.external.QuotesResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.ResponseQuotes;
import org.akuunda.akuundawallet.wallet.api.requests.OnrampMoneyRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface OnrampMoneyServiceClient {
    ResponseEntity<OnrampMoneyResponse> generateWidgetLink(OnrampMoneyRequest request);

    ResponseEntity<CountryConfigDto> fetchAllCountryConfig();
    ResponseEntity<Map<String, Integer>> verifyIfIsActive(final String codePays);

    ResponseEntity<ResponseQuotes> getQuotes(QuoteRequestDTO request);
}
