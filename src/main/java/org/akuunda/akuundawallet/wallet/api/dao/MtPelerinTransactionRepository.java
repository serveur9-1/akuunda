package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.MtPelerinTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
@Repository
public interface MtPelerinTransactionRepository extends JpaRepository<MtPelerinTransaction, Long> {
    
    /**
     * Trouve une transaction par son merchant_oid
     */
    Optional<MtPelerinTransaction> findByMerchantOid(String merchantOid);
    
    /**
     * Trouve toutes les transactions par statut
     */
    List<MtPelerinTransaction> findByStatusOrderByCreationDateDesc(String status);
    
    /**
     * Trouve toutes les transactions par type
     */
    List<MtPelerinTransaction> findByTransactionTypeOrderByCreationDateDesc(String transactionType);
    
    /**
     * Trouve toutes les transactions d'un utilisateur
     */
    List<MtPelerinTransaction> findByUsernameOrderByCreationDateDesc(String username);
}

