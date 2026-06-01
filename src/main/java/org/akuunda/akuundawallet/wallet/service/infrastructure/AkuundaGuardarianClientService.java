package org.akuunda.akuundawallet.wallet.service.infrastructure;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import java.util.Map;

@Validated
public interface AkuundaGuardarianClientService {

    ResponseEntity<StatusResponse> getStatus();

    ResponseEntity<List<CurrencyResponse>> getCurrencies();

    ResponseEntity<List<MarketResponse>> getMarketInfo();

    ResponseEntity<EstimateResponse> getEstimate(@Valid EstimateRequest estimateRequest);

    ResponseEntity<GuadarianResponse> deposit(TransactionRequest transactionRequest);
    ResponseEntity<GuadarianResponse> withdraw(TransactionRequest transactionRequest);


    ResponseEntity<List<PaymentCategoryResponse>> getPaymentCategories();

    ResponseEntity<TransactionStatusResponse> getTransactionById(String transactionId, String username);
    
    /**
     * Récupère une transaction Guardarian au format simplifié
     * @param transactionId ID de la transaction
     * @param username Nom d'utilisateur pour vérifier la propriété
     * @return Transaction au format simplifié (id, status, date, amount, currency)
     */
    ResponseEntity<SimpleTransactionResponse> getSimpleTransactionById(String transactionId, String username);
    
    /**
     * Récupère l'historique des transactions Guardarian pour un utilisateur au format simplifié
     * @param username Nom d'utilisateur (obligatoire pour sécurité)
     * @param fromDate Date de début (optionnel)
     * @param toDate Date de fin (optionnel)
     * @param skip Nombre de transactions à ignorer (pagination, optionnel)
     * @param limit Nombre maximum de transactions à retourner (pagination, optionnel)
     * @return Liste des transactions au format simplifié (id, status, date, amount, currency)
     */
    ResponseEntity<List<SimpleTransactionResponse>> getSimpleTransactions(
            String username,
            java.time.LocalDateTime fromDate,
            java.time.LocalDateTime toDate,
            Integer skip,
            Integer limit);

    ResponseEntity<Map<String, Object>> detectTransactionType(String fromCurrency, String toCurrency);

    ResponseEntity<GuadarianResponse> paiement(@Valid GuardiaranPaiement request);
}
