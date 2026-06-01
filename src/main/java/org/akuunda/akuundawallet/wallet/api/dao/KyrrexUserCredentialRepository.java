package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.KyrrexUserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository pour les credentials Kyrrex des utilisateurs Akuunda.
 */
@Repository
public interface KyrrexUserCredentialRepository extends JpaRepository<KyrrexUserCredential, Long> {

    /**
     * Lookup principal : credentials non révoqués pour un username.
     * Equivalent à l'ancien findByUsernameAndActiveTrueAndRevokedAtIsNull
     * puisque isActive() est désormais dérivé de revokedAt == null.
     */
    Optional<KyrrexUserCredential> findByUsernameAndRevokedAtIsNull(String username);

    /**
     * Lookup sans filtre (admin/debug).
     */
    Optional<KyrrexUserCredential> findByUsername(String username);

    /**
     * Vérifie l'existence de credentials pour un username.
     */
    boolean existsByUsername(String username);
}
