package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.PartnerContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerContractRepository extends JpaRepository<PartnerContract, Long> {
    Optional<PartnerContract> findByContractCode(String contractCode);
    List<PartnerContract> findByPartnerUsernameAndActiveTrue(String partnerUsername);
    boolean existsByContractCode(String contractCode);
}
