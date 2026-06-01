package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.entities.PartnerContractPayment;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;

public interface PartnerPaymentProcessingService {
    void processPayment(PartnerContractPayment payment, Wallet serviceWallet);
}
