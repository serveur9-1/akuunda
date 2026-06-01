package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.SavedPaymentAccountPayload;

import java.util.List;

public interface SavedPaymentAccountService {

    List<SavedPaymentAccountPayload> listForUser(String username);

    SavedPaymentAccountPayload upsert(String username, SavedPaymentAccountPayload payload);

    void delete(String username, String id);
}
