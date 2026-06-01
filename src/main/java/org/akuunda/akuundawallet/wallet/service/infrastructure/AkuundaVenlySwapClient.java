package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.TokenPair;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
public interface AkuundaVenlySwapClient {
    /**
     * Récupère les paires de tokens disponibles pour un portefeuille donné.
     *
     * @param walletId L'identifiant du portefeuille.
     * @return ResponseEntity contenant les paires de tokens.
     */
    ResponseEntity<List<TokenPair>> retrieveTokenPairs(String walletId);

    /**
     * Récupère le taux d'échange pour une paire de tokens donnée.
     *
     * @param fromSecretType Blockchain du token source.
     * @param toSecretType Blockchain du token destination.
     * @param fromToken Adresse du token source.
     * @param toToken Adresse du token destination.
     * @param amount Montant à échanger.
     * @param orderType Type de l'ordre (SELL).
     * @return ResponseEntity contenant les informations sur le taux d'échange.
     */
    ResponseEntity<Object> retrieveExchangeRate(String fromSecretType, String toSecretType, String fromToken, String toToken, int amount, String orderType);

    /**
     * Construit les transactions nécessaires pour effectuer un swap.
     *
     * @param walletId L'identifiant du portefeuille source.
     * @param requestBody Le corps de la requête contenant les détails du swap.
     * @return ResponseEntity contenant les transactions nécessaires.
     */
    ResponseEntity<Object> buildSwapTransaction(String walletId, Object requestBody);

    /**
     * Exécute une transaction de swap.
     *
     * @param requestBody Le corps de la requête contenant les détails de la transaction.
     * @param signingMethodHeader L'en-tête Signing-Method requis pour l'exécution.
     * @return ResponseEntity contenant le résultat de l'exécution de la transaction.
     */
    ResponseEntity<Object> executeSwapTransaction(Object requestBody, String signingMethodHeader);
}
