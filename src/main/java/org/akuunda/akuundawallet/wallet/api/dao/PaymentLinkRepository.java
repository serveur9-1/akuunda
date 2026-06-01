package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.PaymentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, Long> {

    Optional<PaymentLink> findByUniqueCode(String uniqueCode);

    @Query("SELECT p FROM PaymentLink p LEFT JOIN FETCH p.creator WHERE p.creator = :creator ORDER BY p.createdAt DESC")
    List<PaymentLink> findByCreatorOrderByCreatedAtDesc(@Param("creator") Users creator);

    @Query("SELECT p FROM PaymentLink p LEFT JOIN FETCH p.creator WHERE p.creator = :creator AND p.isActive = true ORDER BY p.createdAt DESC")
    List<PaymentLink> findByCreatorAndIsActiveTrueOrderByCreatedAtDesc(@Param("creator") Users creator);

    boolean existsByUniqueCode(String uniqueCode);
}

