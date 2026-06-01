package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.MeldTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
public interface MeldTransactionRepository extends JpaRepository<MeldTransaction, String> {

    Optional<MeldTransaction> findByTransactionId(String transactionId);

    Optional<MeldTransaction> findByExternalCustomerId(String externalCustomerId);

    /**
     * Retrouver les transactions Meld dont le statut est dans la liste donnée.
     * Ex: ["PENDING", "PENDING_CREATED", "PROCESSING", "SETTLING"]
     */
    List<MeldTransaction> findByStatusIn(List<String> statuses);
}
