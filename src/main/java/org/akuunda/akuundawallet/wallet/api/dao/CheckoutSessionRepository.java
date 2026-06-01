package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.CheckoutSession;
import org.akuunda.akuundawallet.wallet.api.entities.MerchantApiKey;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {
    Optional<CheckoutSession> findByCheckoutCode(String checkoutCode);

    // JOIN FETCH merchantApiKey, permanentLink et paymentSession : 3 associations EAGER sur CheckoutSession.
    // Sans JOIN FETCH, chaque CheckoutSession dans la boucle du polling task générait 3 SELECT supplémentaires.
    @Query("SELECT cs FROM CheckoutSession cs " +
           "LEFT JOIN FETCH cs.merchantApiKey mk LEFT JOIN FETCH mk.merchant LEFT JOIN FETCH mk.permanentLink " +
           "LEFT JOIN FETCH cs.permanentLink pl LEFT JOIN FETCH pl.creator " +
           "LEFT JOIN FETCH cs.paymentSession ps LEFT JOIN FETCH ps.permanentLink pspl LEFT JOIN FETCH pspl.creator " +
           "WHERE cs.status IN :statuses")
    List<CheckoutSession> findByStatusIn(@Param("statuses") List<String> statuses);

    // Appelé en boucle dans PermanentLinkDepositPollingTask pour chaque session — JOIN FETCH indispensable.
    @Query("SELECT cs FROM CheckoutSession cs " +
           "LEFT JOIN FETCH cs.merchantApiKey mk LEFT JOIN FETCH mk.merchant LEFT JOIN FETCH mk.permanentLink " +
           "LEFT JOIN FETCH cs.permanentLink pl LEFT JOIN FETCH pl.creator " +
           "LEFT JOIN FETCH cs.paymentSession ps LEFT JOIN FETCH ps.permanentLink pspl LEFT JOIN FETCH pspl.creator " +
           "WHERE cs.paymentSession = :paymentSession")
    Optional<CheckoutSession> findByPaymentSession(@Param("paymentSession") PermanentLinkSession paymentSession);

    Optional<CheckoutSession> findByMerchantApiKeyAndIdempotencyKey(MerchantApiKey merchantApiKey, String idempotencyKey);
}
