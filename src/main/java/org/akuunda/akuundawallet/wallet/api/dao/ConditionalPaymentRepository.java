package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
public interface ConditionalPaymentRepository extends JpaRepository<ConditionalPayment, Long> {
    
    Optional<ConditionalPayment> findByPaymentCode(String paymentCode);

    // JOIN FETCH sur les 5 associations EAGER (client, vendor, 3 wallets) : évite 5 SELECT par entité.
    @Query("SELECT cp FROM ConditionalPayment cp " +
           "LEFT JOIN FETCH cp.client LEFT JOIN FETCH cp.vendor " +
           "LEFT JOIN FETCH cp.intermediateWallet LEFT JOIN FETCH cp.clientWallet LEFT JOIN FETCH cp.vendorWallet " +
           "WHERE cp.client = :client")
    List<ConditionalPayment> findByClient(@Param("client") Users client);

    @Query("SELECT cp FROM ConditionalPayment cp " +
           "LEFT JOIN FETCH cp.client LEFT JOIN FETCH cp.vendor " +
           "LEFT JOIN FETCH cp.intermediateWallet LEFT JOIN FETCH cp.clientWallet LEFT JOIN FETCH cp.vendorWallet " +
           "WHERE cp.vendor = :vendor")
    List<ConditionalPayment> findByVendor(@Param("vendor") Users vendor);

    @Query("SELECT cp FROM ConditionalPayment cp " +
           "LEFT JOIN FETCH cp.client LEFT JOIN FETCH cp.vendor " +
           "LEFT JOIN FETCH cp.intermediateWallet LEFT JOIN FETCH cp.clientWallet LEFT JOIN FETCH cp.vendorWallet " +
           "WHERE cp.status = :status")
    List<ConditionalPayment> findByStatus(@Param("status") String status);

    @Query("SELECT cp FROM ConditionalPayment cp " +
           "LEFT JOIN FETCH cp.client LEFT JOIN FETCH cp.vendor " +
           "LEFT JOIN FETCH cp.intermediateWallet LEFT JOIN FETCH cp.clientWallet LEFT JOIN FETCH cp.vendorWallet " +
           "WHERE cp.client = :user OR cp.vendor = :user")
    List<ConditionalPayment> findByClientOrVendor(@Param("user") Users user);

    @Query("SELECT cp FROM ConditionalPayment cp WHERE cp.clientWallet.id IN :walletIds " +
           "OR cp.vendorWallet.id IN :walletIds OR cp.intermediateWallet.id IN :walletIds")
    List<ConditionalPayment> findByWalletIds(@Param("walletIds") List<String> walletIds);

    @Query("SELECT cp FROM ConditionalPayment cp " +
           "LEFT JOIN FETCH cp.client LEFT JOIN FETCH cp.vendor " +
           "LEFT JOIN FETCH cp.intermediateWallet LEFT JOIN FETCH cp.clientWallet LEFT JOIN FETCH cp.vendorWallet " +
           "WHERE cp.status = :status AND cp.serviceStartDate <= :date")
    List<ConditionalPayment> findPendingPaymentsBeforeDate(@Param("status") String status, @Param("date") java.time.LocalDateTime date);

    // Remplace findAll(Pageable) dans le backoffice : ajoute JOIN FETCH pour éviter N×5 SELECTs parasites.
    @Query(value = "SELECT cp FROM ConditionalPayment cp " +
                   "LEFT JOIN FETCH cp.client LEFT JOIN FETCH cp.vendor " +
                   "LEFT JOIN FETCH cp.intermediateWallet LEFT JOIN FETCH cp.clientWallet LEFT JOIN FETCH cp.vendorWallet",
           countQuery = "SELECT COUNT(cp) FROM ConditionalPayment cp")
    Page<ConditionalPayment> findAllWithFetch(Pageable pageable);
}

