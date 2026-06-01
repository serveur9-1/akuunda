package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentQuoteRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldQuoteResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldSessionResponse;
import org.springframework.http.ResponseEntity;

public interface OneTimePaymentMeldService {

    ResponseEntity<MeldQuoteResponse> getQuotesForPaymentLink(String uniqueCode, OneTimePaymentQuoteRequest request);

    ResponseEntity<MeldSessionResponse> createPaymentSession(String uniqueCode, OneTimePaymentPayRequest request);
}
