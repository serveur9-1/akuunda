package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.SavedPaymentAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedPaymentAccountRepository extends JpaRepository<SavedPaymentAccount, String> {

    List<SavedPaymentAccount> findByUsernameOrderByUpdatedAtDesc(String username);

    void deleteByUsernameAndId(String username, String id);
}
