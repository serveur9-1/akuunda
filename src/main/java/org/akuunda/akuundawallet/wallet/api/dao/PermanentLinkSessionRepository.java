package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermanentLinkSessionRepository extends JpaRepository<PermanentLinkSession, Long> {
    Optional<PermanentLinkSession> findBySessionCode(String sessionCode);

    Optional<PermanentLinkSession> findByExternalTransactionId(String externalTransactionId);

    @Query("SELECT s FROM PermanentLinkSession s LEFT JOIN FETCH s.permanentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l = :permanentLink ORDER BY s.createdAt DESC")
    List<PermanentLinkSession> findByPermanentLinkOrderByCreatedAtDesc(@Param("permanentLink") PermanentLink permanentLink);

    @Query("SELECT s FROM PermanentLinkSession s LEFT JOIN FETCH s.permanentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l = :permanentLink AND s.status = :status ORDER BY s.createdAt DESC")
    List<PermanentLinkSession> findByPermanentLinkAndStatusOrderByCreatedAtDesc(@Param("permanentLink") PermanentLink permanentLink, @Param("status") String status);

    @Query(value = "SELECT s FROM PermanentLinkSession s LEFT JOIN FETCH s.permanentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l = :permanentLink ORDER BY s.createdAt DESC",
           countQuery = "SELECT COUNT(s) FROM PermanentLinkSession s WHERE s.permanentLink = :permanentLink")
    Page<PermanentLinkSession> findAllByPermanentLinkOrderByCreatedAtDesc(@Param("permanentLink") PermanentLink permanentLink, Pageable pageable);

    @Query(value = "SELECT s FROM PermanentLinkSession s LEFT JOIN FETCH s.permanentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE l = :permanentLink AND s.status = :status ORDER BY s.createdAt DESC",
           countQuery = "SELECT COUNT(s) FROM PermanentLinkSession s WHERE s.permanentLink = :permanentLink AND s.status = :status")
    Page<PermanentLinkSession> findAllByPermanentLinkAndStatusOrderByCreatedAtDesc(@Param("permanentLink") PermanentLink permanentLink, @Param("status") String status, Pageable pageable);
    boolean existsBySessionCode(String sessionCode);
    long countByPermanentLinkAndStatus(PermanentLink permanentLink, String status);

    @Query("SELECT s FROM PermanentLinkSession s LEFT JOIN FETCH s.permanentLink l LEFT JOIN FETCH l.creator c LEFT JOIN FETCH c.countryCurrency WHERE s.status IN :statuses")
    List<PermanentLinkSession> findByStatusIn(@Param("statuses") List<String> statuses);

    Optional<PermanentLinkSession> findByCreate2WalletAddress(String create2WalletAddress);

    /** Expire en masse les sessions dont expiresAt est dépassé. */
    @Modifying
    @Query("UPDATE PermanentLinkSession s SET s.status = 'EXPIRED', s.updatedAt = :now " +
           "WHERE s.status IN ('CREATED', 'PENDING') AND s.expiresAt IS NOT NULL AND s.expiresAt < :now")
    int bulkExpireByExpiresAt(@Param("now") LocalDateTime now);

    /** Expire les sessions sans expiresAt créées avant la date limite. */
    @Modifying
    @Query("UPDATE PermanentLinkSession s SET s.status = 'EXPIRED', s.updatedAt = :now " +
           "WHERE s.status IN ('CREATED', 'PENDING') AND s.expiresAt IS NULL AND s.createdAt < :fallback")
    int bulkExpireOrphaned(@Param("now") LocalDateTime now, @Param("fallback") LocalDateTime fallback);
}
