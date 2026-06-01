package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.PartnerPaymentBeneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartnerPaymentBeneficiaryRepository extends JpaRepository<PartnerPaymentBeneficiary, Long> {
    List<PartnerPaymentBeneficiary> findByPaymentIdOrderByExecutionOrderAsc(Long paymentId);
    void deleteByPaymentId(Long paymentId);
}
