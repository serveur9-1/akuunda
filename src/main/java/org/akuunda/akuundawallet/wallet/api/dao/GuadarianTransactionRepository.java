package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.GuardarianTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface GuadarianTransactionRepository extends JpaRepository<GuardarianTransaction,Long> {
    Optional<GuardarianTransaction> findByExternalTransactionId(Long id);
    Optional<GuardarianTransaction> findByExternalTransactionIdAndUsername(Long id, String username);
    List<GuardarianTransaction> findByUsernameOrderByCreatedAtDesc(String username);
}
