package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPayment;
import org.akuunda.akuundawallet.wallet.api.entities.QRCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
public interface QRCodeRepository extends JpaRepository<QRCode, Long> {

    // JOIN FETCH sur conditionalPayment.client et vendor (+ countryCurrency) :
    // évite 4 SELECT secondaires par QRCode à cause des associations EAGER en cascade.
    @Query("SELECT q FROM QRCode q " +
           "LEFT JOIN FETCH q.conditionalPayment cp " +
           "LEFT JOIN FETCH cp.client c LEFT JOIN FETCH c.countryCurrency " +
           "LEFT JOIN FETCH cp.vendor v LEFT JOIN FETCH v.countryCurrency " +
           "WHERE q.token = :token")
    Optional<QRCode> findByToken(@Param("token") String token);

    @Query("SELECT q FROM QRCode q " +
           "LEFT JOIN FETCH q.conditionalPayment cp " +
           "LEFT JOIN FETCH cp.client c LEFT JOIN FETCH c.countryCurrency " +
           "LEFT JOIN FETCH cp.vendor v LEFT JOIN FETCH v.countryCurrency " +
           "WHERE cp = :conditionalPayment")
    Optional<QRCode> findByConditionalPayment(@Param("conditionalPayment") ConditionalPayment conditionalPayment);
}
