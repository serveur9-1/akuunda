package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.external.AkuundaTransfertRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.SimpleTransactionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.TransactionHistoryResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.TransactionStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Validated
public interface AkuundaTransactionService {

    ResponseEntity<TransactionStatusResponse> checkTransactionStatus(String username, String transactionId, String transactionType);

    /**
     * Récupère toutes les transactions de tous les opérateurs (MT Pelerin, YellowCard, Guardarian)
     * pour un utilisateur donné, dans un format unifié.
     *
     * @param username Nom d'utilisateur (obligatoire)
     * @param fromDate Date de début (optionnel)
     * @param toDate Date de fin (optionnel)
     * @param skip Nombre de transactions à ignorer pour la pagination (optionnel, défaut: 0)
     * @param limit Nombre maximum de transactions à retourner (optionnel, défaut: 100)
     * @return Réponse standard avec status, message et data (liste de transactions)
     */
    ResponseEntity<TransactionHistoryResponse> getAllTransactions(
            String username,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Integer skip,
            Integer limit
    );

    ResponseEntity<String> executeTransfertCpteACpte(AkuundaTransfertRequest request);
}
