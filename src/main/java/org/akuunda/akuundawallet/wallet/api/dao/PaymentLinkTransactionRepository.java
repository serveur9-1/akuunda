package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.PaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PaymentLinkTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentLinkTransactionRepository extends JpaRepository<PaymentLinkTransaction, Long> {

    // JOIN FETCH sur paymentLink.creator.countryCurrency et payer.countryCurrency :
    // évite 4 SELECT secondaires par transaction à cause des associations EAGER.
    @Query("SELECT t FROM PaymentLinkTransaction t " +
           "LEFT JOIN FETCH t.paymentLink pl LEFT JOIN FETCH pl.creator plc LEFT JOIN FETCH plc.countryCurrency " +
           "LEFT JOIN FETCH t.payer p LEFT JOIN FETCH p.countryCurrency " +
           "WHERE t.paymentLink = :paymentLink ORDER BY t.createdAt DESC")
    List<PaymentLinkTransaction> findByPaymentLinkOrderByCreatedAtDesc(@Param("paymentLink") PaymentLink paymentLink);

    @Query("SELECT t FROM PaymentLinkTransaction t " +
           "LEFT JOIN FETCH t.paymentLink pl LEFT JOIN FETCH pl.creator plc LEFT JOIN FETCH plc.countryCurrency " +
           "LEFT JOIN FETCH t.payer p LEFT JOIN FETCH p.countryCurrency " +
           "WHERE t.paymentLink = :paymentLink AND t.status = :status ORDER BY t.createdAt DESC")
    List<PaymentLinkTransaction> findByPaymentLinkAndStatusOrderByCreatedAtDesc(
            @Param("paymentLink") PaymentLink paymentLink, @Param("status") String status);

    @Query("SELECT t FROM PaymentLinkTransaction t " +
           "LEFT JOIN FETCH t.paymentLink pl LEFT JOIN FETCH pl.creator plc LEFT JOIN FETCH plc.countryCurrency " +
           "LEFT JOIN FETCH t.payer p LEFT JOIN FETCH p.countryCurrency " +
           "WHERE t.payer = :payer")
    List<PaymentLinkTransaction> findByPayer(@Param("payer") Users payer);
}
