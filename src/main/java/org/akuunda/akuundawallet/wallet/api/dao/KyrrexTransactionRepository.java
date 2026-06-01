package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KyrrexTransactionRepository extends JpaRepository<KyrrexTransaction, Long> {

    Optional<KyrrexTransaction> findByKyrrexId(String kyrrexId);

    Optional<KyrrexTransaction> findBySequenceId(String sequenceId);

    Page<KyrrexTransaction> findByUsername(String username, Pageable pageable);

    Page<KyrrexTransaction> findByUsernameAndType(
            String username, KyrrexTransactionType type, Pageable pageable);

    List<KyrrexTransaction> findByUsernameAndStatus(String username, String status);

    List<KyrrexTransaction> findByStatusIn(List<String> statuses);

    List<KyrrexTransaction> findByUsernameOrderByCreatedAtDesc(String username);

    /**
     * Liste des transactions Kyrrex en statut terminal de succès qui n'ont pas
     * encore été créditées sur le wallet Akuunda Pay (crédit ledger).
     * Utilisé par le scheduler de réconciliation pour rattraper les webhooks
     * manqués ou les erreurs transitoires lors du règlement.
     */
    List<KyrrexTransaction> findByStatusInAndSettledAtIsNull(List<String> statuses);

    /**
     * Liste des transactions par type et statut non encore réglées.
     * Utile pour cibler la réconciliation sur des dépôts uniquement, sans
     * embarquer les retraits ou les ordres techniques.
     */
    List<KyrrexTransaction> findByTypeInAndStatusInAndSettledAtIsNull(
            List<KyrrexTransactionType> types, List<String> statuses);

    Optional<KyrrexTransaction> findByPayoutWithdrawalId(String payoutWithdrawalId);
}
