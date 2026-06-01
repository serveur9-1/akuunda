package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.CheckoutRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutSessionResponse;
import org.springframework.http.ResponseEntity;

public interface CheckoutService {
    ResponseEntity<CheckoutResponse> createCheckout(String apiKey, CheckoutRequest request);
    ResponseEntity<CheckoutSessionResponse> getCheckoutByCode(String checkoutCode);
}
