package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransaction;

/**
 * Orchestre l'automatisation post-dépôt Kyrrex : dès qu'un dépôt Kyrrex
 * passe en statut terminal de succès, le wallet Akuunda Pay de l'utilisateur
 * est crédité immédiatement (modèle ledger-first), puis le sweep on-chain
 * USDC/Polygon est traité en back-office.
 *
 * <p>Le service est strictement idempotent : appelé plusieurs fois pour le
 * même {@link KyrrexTransaction}, il ne crédite le wallet qu'une seule fois
 * grâce au flag {@code settled_at}.</p>
 */
public interface KyrrexDepositSettlementService {

    /**
     * Tente de régler une transaction Kyrrex sur le wallet Akuunda Pay.
     * <ul>
     *     <li>No-op si la feature est désactivée.</li>
     *     <li>No-op si la transaction est déjà réglée (idempotence).</li>
     *     <li>No-op si le statut Kyrrex n'est pas un succès terminal.</li>
     *     <li>Sinon : crédite le wallet (USDC + devise locale), met l'opération
     *         en {@code VALIDEE} et envoie une notification push à l'utilisateur.</li>
     * </ul>
     *
     * @param transaction la transaction Kyrrex à régler
     * @return {@code true} si le règlement vient d'être effectué par cet appel,
     *         {@code false} dans tous les autres cas (déjà réglé, statut non
     *         terminal, désactivé, transaction invalide, erreur applicative).
     */
    boolean settleIfEligible(KyrrexTransaction transaction);

    /**
     * Variante par identifiant Kyrrex (UID renvoyé par l'API Kyrrex et
     * utilisé comme {@code operationHash} côté Akuunda).
     * Utilisée par le webhook quand on a juste l'UID de l'événement.
     */
    boolean settleIfEligibleByKyrrexId(String kyrrexId);
}
