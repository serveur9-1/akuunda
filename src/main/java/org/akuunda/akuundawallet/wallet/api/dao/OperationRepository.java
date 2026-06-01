package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.time.LocalDateTime;
import java.util.List;

@CrossOrigin("*")
@RepositoryRestResource
public interface OperationRepository extends JpaRepository<Operation, Long> {

    Page<Operation> findByUsernameAndType(String username, String operationType, Pageable pageable);

    Page<Operation> findByUsername(String username, Pageable pageable);

    Operation findByOperationHash(String operationHash);

    /**
     * Trouve les opérations dont le statut est "en cours" (PENDING, INITIALED, NEW, EN ATTENTE)
     * et dont la date de création est antérieure à la date donnée (timeout).
     * Utilisé pour passer automatiquement en échec les opérations non abouties.
     */
    List<Operation> findByStatusInAndCreatedAtBefore(List<String> statuses, LocalDateTime createdAt);

    List<Operation> findByStatusIn(List<String> statuses);

    /**
     * Trouve la dernière opération d'un utilisateur pour une désignation donnée,
     * triée par date de création décroissante.
     * Utilisé comme fallback dans YellowCardEventWebhookController
     * quand findByOperationHash() ne trouve rien (cas des collections simples YC-SIMPLE-*).
     */
    Operation findFirstByUsernameAndDesignationOrderByCreatedAtDesc(String username, String designation);
    
    List<Operation> findByDesignationAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            String designation, String status, LocalDateTime after);

    long countByStatusIn(List<String> statuses);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.devise, SUM(o.amount) FROM Operation o WHERE o.amount IS NOT NULL AND o.status IN ('VALIDEE', 'SUCCESS', 'REUSSIE') GROUP BY o.devise")
    List<Object[]> sumAmountGroupByDevise();

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM("
                    + org.akuunda.akuundawallet.backoffice.util.AdminVolumeUsdConverter.USD_VOLUME_CASE_O
                    + "), 0) FROM Operation o "
                    + "WHERE o.status IN ('VALIDEE', 'SUCCESS', 'REUSSIE', 'COMPLETED', 'Complétée') "
                    + "AND ((o.amount IS NOT NULL AND o.amount > 0) "
                    + "     OR (o.providerAmount IS NOT NULL AND o.providerAmount > 0))")
    Double sumTotalVolumeUsdSuccessful();

    @org.springframework.data.jpa.repository.Query(
            "SELECT YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt), "
                    + "COALESCE(SUM("
                    + org.akuunda.akuundawallet.backoffice.util.AdminVolumeUsdConverter.USD_VOLUME_CASE_O
                    + "), 0) FROM Operation o "
                    + "WHERE o.createdAt >= :since "
                    + "AND o.status IN ('VALIDEE', 'SUCCESS', 'REUSSIE', 'COMPLETED', 'Complétée') "
                    + "AND ((o.amount IS NOT NULL AND o.amount > 0) "
                    + "     OR (o.providerAmount IS NOT NULL AND o.providerAmount > 0)) "
                    + "GROUP BY YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt)")
    List<Object[]> sumVolumeUsdByDay(
            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.operationHash, o.designation, o.devise, o.amount FROM Operation o " +
            "WHERE o.status IN ('VALIDEE', 'SUCCESS', 'REUSSIE') " +
            "AND o.amount IS NOT NULL AND o.devise IS NOT NULL AND o.amount > 0.5")
    List<Object[]> findSuccessfulOpsForVolume();

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.devise, COUNT(o), SUM(o.amount) FROM Operation o WHERE o.devise IS NOT NULL GROUP BY o.devise")
    List<Object[]> countAndSumByDevise();

    @org.springframework.data.jpa.repository.Query(
            "SELECT YEAR(o.createdAt), MONTH(o.createdAt), COUNT(o) FROM Operation o " +
            "WHERE o.createdAt >= :since " +
            "GROUP BY YEAR(o.createdAt), MONTH(o.createdAt) " +
            "ORDER BY YEAR(o.createdAt), MONTH(o.createdAt)")
    List<Object[]> countByMonth(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    @org.springframework.data.jpa.repository.Query(
            "SELECT YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt), COALESCE(SUM(o.amount), 0) FROM Operation o " +
            "WHERE o.createdAt >= :since AND o.amount IS NOT NULL " +
            "GROUP BY YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt)")
    List<Object[]> sumAmountByDay(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    @org.springframework.data.jpa.repository.Query(
            "SELECT YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt), COALESCE(SUM(o.amount), 0) FROM Operation o " +
            "WHERE o.createdAt >= :since AND o.amount IS NOT NULL AND o.username = :username " +
            "GROUP BY YEAR(o.createdAt), MONTH(o.createdAt), DAY(o.createdAt)")
    List<Object[]> sumAmountByDayForUsername(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.designation, COUNT(o) FROM Operation o WHERE o.designation IS NOT NULL GROUP BY o.designation")
    List<Object[]> countByDesignation();

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.username, COUNT(o), SUM(o.amount) FROM Operation o " +
            "WHERE o.amount IS NOT NULL AND o.username IS NOT NULL " +
            "GROUP BY o.username ORDER BY SUM(o.amount) DESC")
    List<Object[]> topUsersByVolume(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.devise, o.type, COUNT(o) FROM Operation o " +
            "WHERE o.devise IS NOT NULL AND o.type IS NOT NULL " +
            "GROUP BY o.devise, o.type")
    List<Object[]> countByDeviseAndType();

    /** Returns (username, txCount, totalAmount) grouped by username */
    @org.springframework.data.jpa.repository.Query(
            "SELECT o.username, COUNT(o), COALESCE(SUM(o.amount), 0) FROM Operation o " +
            "WHERE o.username IS NOT NULL " +
            "GROUP BY o.username")
    List<Object[]> countAndSumByUsername();

    /** Returns (username, txCount, totalAmount, devise) — top N by volume, per username+devise */
    @org.springframework.data.jpa.repository.Query(
            "SELECT o.username, COUNT(o), SUM(o.amount), o.devise FROM Operation o " +
            "WHERE o.amount IS NOT NULL AND o.username IS NOT NULL AND o.devise IS NOT NULL " +
            "GROUP BY o.username, o.devise ORDER BY SUM(o.amount) DESC")
    List<Object[]> topUsersByVolumeWithDevise(org.springframework.data.domain.Pageable pageable);

    // ---- Clients d'un marchand (contreparties distinctes) ----

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.counterpartUsername, o.counterpartDisplayName, COUNT(o), COALESCE(SUM(o.amount), 0), MAX(o.createdAt) " +
            "FROM Operation o WHERE o.username = :username AND o.counterpartUsername IS NOT NULL " +
            "GROUP BY o.counterpartUsername, o.counterpartDisplayName " +
            "ORDER BY MAX(o.createdAt) DESC")
    List<Object[]> findDistinctCounterparts(
            @org.springframework.data.repository.query.Param("username") String username,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(DISTINCT o.counterpartUsername) FROM Operation o " +
            "WHERE o.username = :username AND o.counterpartUsername IS NOT NULL")
    long countDistinctCounterparts(
            @org.springframework.data.repository.query.Param("username") String username);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.counterpartUsername, o.counterpartDisplayName, COUNT(o), COALESCE(SUM(o.amount), 0), MAX(o.createdAt) " +
            "FROM Operation o WHERE o.username = :username AND o.counterpartUsername IS NOT NULL " +
            "AND (LOWER(o.counterpartUsername) LIKE CONCAT('%',:q,'%') OR LOWER(o.counterpartDisplayName) LIKE CONCAT('%',:q,'%')) " +
            "GROUP BY o.counterpartUsername, o.counterpartDisplayName " +
            "ORDER BY MAX(o.createdAt) DESC")
    List<Object[]> findDistinctCounterpartsBySearch(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("q") String q,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(DISTINCT o.counterpartUsername) FROM Operation o " +
            "WHERE o.username = :username AND o.counterpartUsername IS NOT NULL " +
            "AND (LOWER(o.counterpartUsername) LIKE CONCAT('%',:q,'%') OR LOWER(o.counterpartDisplayName) LIKE CONCAT('%',:q,'%'))")
    long countDistinctCounterpartsBySearch(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("q") String q);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o.counterpartUsername, o.counterpartDisplayName, COUNT(o), COALESCE(SUM(o.amount), 0), MAX(o.createdAt) " +
            "FROM Operation o WHERE o.username = :username AND o.counterpartUsername = :counterpartUsername " +
            "GROUP BY o.counterpartUsername, o.counterpartDisplayName " +
            "ORDER BY MAX(o.createdAt) DESC")
    java.util.Optional<Object[]> findCounterpartStats(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("counterpartUsername") String counterpartUsername);

    // ---- Filter methods (Hibernate 6 compatible — no IS NULL on params) ----

    Page<Operation> findByDesignationContainingIgnoreCase(String designation, Pageable pageable);

    Page<Operation> findByStatusIgnoreCase(String status, Pageable pageable);

    Page<Operation> findByUsernameAndStatusIgnoreCase(String username, String status, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM Operation o WHERE " +
            "UPPER(o.designation) LIKE UPPER(CONCAT('%',:designation,'%')) AND UPPER(o.status) = UPPER(:status)")
    Page<Operation> findByDesignationAndStatus(
            @org.springframework.data.repository.query.Param("designation") String designation,
            @org.springframework.data.repository.query.Param("status") String status,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM Operation o WHERE o.username = :username AND " +
            "UPPER(o.designation) LIKE UPPER(CONCAT('%',:designation,'%')) AND UPPER(o.status) = UPPER(:status)")
    Page<Operation> findByUsernameAndDesignationAndStatus(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("designation") String designation,
            @org.springframework.data.repository.query.Param("status") String status,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM Operation o WHERE " +
            "LOWER(o.username) LIKE CONCAT('%',:q,'%') OR LOWER(o.operationHash) LIKE CONCAT('%',:q,'%')")
    Page<Operation> findBySearch(
            @org.springframework.data.repository.query.Param("q") String q,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM Operation o WHERE " +
            "UPPER(o.designation) LIKE UPPER(CONCAT('%',:designation,'%')) AND " +
            "(LOWER(o.username) LIKE CONCAT('%',:q,'%') OR LOWER(o.operationHash) LIKE CONCAT('%',:q,'%'))")
    Page<Operation> findByDesignationAndSearch(
            @org.springframework.data.repository.query.Param("designation") String designation,
            @org.springframework.data.repository.query.Param("q") String q,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM Operation o WHERE o.username = :username AND " +
            "(LOWER(o.operationHash) LIKE CONCAT('%',:q,'%') OR LOWER(COALESCE(o.counterpartUsername, '')) LIKE CONCAT('%',:q,'%') " +
            "OR LOWER(COALESCE(o.counterpartDisplayName, '')) LIKE CONCAT('%',:q,'%'))")
    Page<Operation> findByUsernameAndSearch(
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("q") String q,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM Operation o WHERE " +
            "UPPER(o.status) = UPPER(:status) AND " +
            "(LOWER(o.username) LIKE CONCAT('%',:q,'%') OR LOWER(o.operationHash) LIKE CONCAT('%',:q,'%'))")
    Page<Operation> findByStatusAndSearch(
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("q") String q,
            Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "SELECT o FROM Operation o WHERE " +
            "UPPER(o.designation) LIKE UPPER(CONCAT('%',:designation,'%')) AND UPPER(o.status) = UPPER(:status) AND " +
            "(LOWER(o.username) LIKE CONCAT('%',:q,'%') OR LOWER(o.operationHash) LIKE CONCAT('%',:q,'%'))")
    Page<Operation> findByDesignationAndStatusAndSearch(
            @org.springframework.data.repository.query.Param("designation") String designation,
            @org.springframework.data.repository.query.Param("status") String status,
            @org.springframework.data.repository.query.Param("q") String q,
            Pageable pageable);
}
