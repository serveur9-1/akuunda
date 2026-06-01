package org.akuunda.akuundawallet.keycloak.api.dto;

/**
 * Représente le solde d'un utilisateur et son équivalent converti.
 */
public record WalletBalanceDto(
        double userBalance,
        double convertedAmount
) {}
