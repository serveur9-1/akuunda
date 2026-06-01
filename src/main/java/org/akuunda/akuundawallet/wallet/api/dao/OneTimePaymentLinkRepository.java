package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OneTimePaymentLinkRepository extends JpaRepository<OneTimePaymentLink, Long> {

    Optional<OneTimePaymentLink> findByUniqueCode(String uniqueCode);

    @Query("SELECT l FROM OneTimePaymentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l.creator = :creator ORDER BY l.createdAt DESC")
    List<OneTimePaymentLink> findByCreatorOrderByCreatedAtDesc(@Param("creator") Users creator);

    boolean existsByUniqueCode(String uniqueCode);

    @Query("SELECT l FROM OneTimePaymentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l.status = :status AND l.expiresAt < :dateTime")
    List<OneTimePaymentLink> findByStatusAndExpiresAtBefore(@Param("status") String status, @Param("dateTime") LocalDateTime dateTime);

    @Query("SELECT l FROM OneTimePaymentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l.status = :status")
    List<OneTimePaymentLink> findByStatus(@Param("status") String status);

    Optional<OneTimePaymentLink> findByCreate2WalletAddress(String create2WalletAddress);

    @Query("SELECT l FROM OneTimePaymentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l.status IN :statuses")
    List<OneTimePaymentLink> findByStatusIn(@Param("statuses") List<String> statuses);

    @Query("SELECT l FROM OneTimePaymentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l.status IN :statuses AND l.linkType = :linkType")
    List<OneTimePaymentLink> findByStatusInAndLinkType(@Param("statuses") List<String> statuses, @Param("linkType") String linkType);

    Optional<OneTimePaymentLink> findByYcSequenceId(String ycSequenceId);

    // ✅ NEW: Find parent link by conditional payment reference
    Optional<OneTimePaymentLink> findByConditionalPaymentId(Long conditionalPaymentId);

    /** Expire en masse les liens dont expiresAt est dépassé — évite de les charger en mémoire. */
    @Modifying
    @Query("UPDATE OneTimePaymentLink l SET l.status = 'EXPIRED', l.updatedAt = :now " +
           "WHERE l.status IN ('CREATED', 'PENDING') AND l.expiresAt IS NOT NULL AND l.expiresAt < :now")
    int bulkExpireByExpiresAt(@Param("now") LocalDateTime now);

    /** Expire les liens sans expiresAt créés avant la date limite (abandons sans délai configuré). */
    @Modifying
    @Query("UPDATE OneTimePaymentLink l SET l.status = 'EXPIRED', l.updatedAt = :now " +
           "WHERE l.status IN ('CREATED', 'PENDING') AND l.expiresAt IS NULL AND l.createdAt < :fallback")
    int bulkExpireOrphaned(@Param("now") LocalDateTime now, @Param("fallback") LocalDateTime fallback);

    /** UPDATE direct par id — évite de recharger le creator (EAGER) juste pour changer le statut. */
    @Modifying
    @Query("UPDATE OneTimePaymentLink l SET l.status = :status, l.updatedAt = :now WHERE l.id = :id")
    void updateStatusById(@Param("id") Long id, @Param("status") String status, @Param("now") LocalDateTime now);

    /** Persiste executeTxHash sans recharger l'entité (évite SELECT creator EAGER). */
    @Modifying
    @Query("UPDATE OneTimePaymentLink l SET l.executeTxHash = :hash, l.updatedAt = :now WHERE l.id = :id")
    void updateExecuteTxHashById(@Param("id") Long id, @Param("hash") String hash, @Param("now") LocalDateTime now);

    /** Persiste depositTxHash sans recharger l'entité (évite SELECT creator EAGER). */
    @Modifying
    @Query("UPDATE OneTimePaymentLink l SET l.depositTxHash = :hash, l.updatedAt = :now WHERE l.id = :id")
    void updateDepositTxHashById(@Param("id") Long id, @Param("hash") String hash, @Param("now") LocalDateTime now);
}
