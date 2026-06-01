package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.WidgetSessionData;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PermanentLinkPaymentService {

    ResponseEntity<Object> initiateYellowCardPayment(String sessionCode, OnRampRequest request);

    ResponseEntity<MeldSessionResponse> initiateMeldPayment(String sessionCode, WidgetSessionData request);

    ResponseEntity<List<PermanentLinkSessionResponse>> getSessionsBySlug(String username, String slug, String status, int page, int size);
}
