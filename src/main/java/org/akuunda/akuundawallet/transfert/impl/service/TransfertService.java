package org.akuunda.akuundawallet.transfert.impl.service;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.springframework.http.ResponseEntity;

public interface TransfertService {

    ResponseEntity<String> verifyAndRefillGasIfNeeded(Wallet wallet, String username);

    /**
     * Vérifie le gas et recharge depuis le wallet sponsor uniquement si {@code operationAmount > 0}
     * et le solde token de l'utilisateur couvre cette opération (retrait, forfait, transfert, etc.).
     * Sans montant d'opération, aucune recharge automatique n'est effectuée.
     */
    ResponseEntity<String> verifyAndRefillGasIfNeeded(Wallet wallet, String username, Double operationAmount);

    ResponseEntity<String> executeGasTransfert(final String sender, final String reiceved,
                                               final Double amount, final Double GasAmount,
                                               final String headerPin, final String devise);

    ResponseEntity<String> executeOnRampTokenTransfert(Users userSender, final String reicevedAdress,
                                                       final Double amount, final Double GasAmount,
                                                       final String headerPin, final String devise);

    /**
     * Transfère du gas (POL natif) depuis un wallet sponsor vers un wallet utilisateur.
     * Utilisé pour la recharge automatique du gas lors des transferts.
     *
     * @param sponsorWalletId    ID Venly du wallet sponsor (Akuunda Funding Wallet)
     * @param destinationAddress Adresse du wallet utilisateur à recharger
     * @param amount             Montant en POL à transférer
     * @param sponsorPin         PIN du wallet sponsor
     * @return ResponseEntity avec le résultat du transfert
     */
    ResponseEntity<String> executeGasRefillFromSponsor(String sponsorWalletId,
                                                       String destinationAddress,
                                                       Double amount,
                                                       String sponsorPin);

    /**
     * Vérifie le gas du wallet associé au username et recharge automatiquement si insuffisant.
     * Point d'entrée unique pour tous les flux nécessitant une transaction Venly.
     */
    ResponseEntity<String> verifyAndRefillGasForUsername(String username);

    ResponseEntity<String> verifyAndRefillGasForUsername(String username, Double operationAmount);
}
