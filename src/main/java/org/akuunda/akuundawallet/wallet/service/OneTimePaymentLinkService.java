package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentConfirmationResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateAndPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateAndPayResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayYcRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayYcResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalLinkYcRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalLinkYcResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalOneTimeLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateOneTimePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.RefundResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface OneTimePaymentLinkService {

    ResponseEntity<OneTimePaymentLinkResponse> createOneTimePaymentLink(String username, CreateOneTimePaymentLinkRequest request);

    ResponseEntity<OneTimePaymentLinkResponse> getOneTimePaymentLinkByCode(String uniqueCode);

    ResponseEntity<List<OneTimePaymentLinkResponse>> getUserOneTimePaymentLinks(String username);

    ResponseEntity<String> cancelOneTimePaymentLink(String username, String uniqueCode);

    ResponseEntity<RefundResponse> refundOneTimePaymentLink(String username, String uniqueCode);

    ResponseEntity<CreateAndPayResponse> createAndPay(String username, CreateAndPayRequest request);

    ResponseEntity<CreateAndPayResponse> createConditionalAndPay(
            String username, CreateConditionalAndPayRequest request);

    ResponseEntity<CreateConditionalAndPayYcResponse> createConditionalAndPayYc(
            String username, CreateConditionalAndPayYcRequest request);

    // 2-step YellowCard flow — Step 1: create the conditional link and pre-register with akuundapay_generelinkYC
    ResponseEntity<CreateConditionalLinkYcResponse> createConditionalLinkYc(
            String username, CreateConditionalLinkYcRequest request);

    ResponseEntity<OneTimePaymentLinkResponse> createConditionalOneTimePaymentLink(
            String username, CreateConditionalOneTimeLinkRequest request);

    ResponseEntity<ConditionalPaymentConfirmationResponse> getConditionalPaymentConfirmation(String uniqueCode);

    ResponseEntity<OneTimePaymentLinkResponse> recoverStuckLink(String uniqueCode);
}
