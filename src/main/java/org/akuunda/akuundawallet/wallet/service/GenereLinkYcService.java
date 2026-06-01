package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkResponse;

public interface GenereLinkYcService {

    /**
     * Calls the akuundapay_generelinkYC backend POST /api/payments/generate-link
     * to pre-register the transaction so the client can complete the payment via the frontend.
     *
     * @param request payment data (recipient, source, amount, currency, country, channelId,
     *                settlementWalletAddress)
     * @return { transactionId, paymentUrl } from akuundapay_generelinkYC
     */
    GenereLinkResponse generateLink(GenereLinkRequest request);
}
