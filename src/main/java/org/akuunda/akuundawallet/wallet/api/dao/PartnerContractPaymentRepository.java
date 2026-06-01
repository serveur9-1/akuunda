package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.PartnerContractPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerContractPaymentRepository extends JpaRepository<PartnerContractPayment, Long> {
    Optional<PartnerContractPayment> findByPaymentCode(String paymentCode);
    Optional<PartnerContractPayment> findByQrToken(String qrToken);
    Optional<PartnerContractPayment> findByConfirmationToken(String confirmationToken);
    List<PartnerContractPayment> findByClientUsernameOrderByCreatedAtDesc(String clientUsername);
    List<PartnerContractPayment> findByContractIdOrderByCreatedAtDesc(Long contractId);

    // JOIN FETCH contract + beneficiaries en une seule requête.
    // DISTINCT nécessaire : LEFT JOIN FETCH sur OneToMany génère N lignes par bénéficiaire → doublons sans DISTINCT.
    @Query("SELECT DISTINCT p FROM PartnerContractPayment p LEFT JOIN FETCH p.contract c LEFT JOIN FETCH c.beneficiaries " +
           "WHERE p.status = :status AND p.autoReleaseScheduledAt < :now AND p.disputedAt IS NULL")
    List<PartnerContractPayment> findByStatusAndAutoReleaseScheduledAtBeforeAndDisputedAtIsNull(
            @Param("status") String status, @Param("now") LocalDateTime now);

    @Query("SELECT DISTINCT p FROM PartnerContractPayment p LEFT JOIN FETCH p.contract c LEFT JOIN FETCH c.beneficiaries " +
           "WHERE p.create2Status = :create2Status AND p.status = :status ORDER BY p.createdAt ASC")
    List<PartnerContractPayment> findByCreate2StatusAndStatusOrderByCreatedAtAsc(
            @Param("create2Status") String create2Status, @Param("status") String status);

    Optional<PartnerContractPayment> findByCreate2WalletAddressIgnoreCase(String create2WalletAddress);
}
