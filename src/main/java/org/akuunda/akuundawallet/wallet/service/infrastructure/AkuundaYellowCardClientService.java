package org.akuunda.akuundawallet.wallet.service.infrastructure;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.wallet.api.dto.WebPaymentInitRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.OffRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.OffRampResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.SimpleTransactionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.TransactionStatusResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.YellowCardWidgetQuoteRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface AkuundaYellowCardClientService {

    /**
     * Creates a transaction status.
     *
     * @param transactionId The transaction id (sequenceId).
     * @param username The username to verify transaction ownership.
     * @return ResponseEntity containing the result of the operation.
     */
    ResponseEntity<TransactionStatusResponse> checkTransactionStatus(final String transactionId, final String username);
    
    /**
     * Récupère une transaction YellowCard au format simplifié
     * @param transactionId ID de la transaction (sequenceId)
     * @param username Nom d'utilisateur pour vérifier la propriété
     * @return Transaction au format simplifié (id, status, date, amount, currency)
     */
    ResponseEntity<SimpleTransactionResponse> getSimpleTransactionStatus(final String transactionId, final String username);
    
    /**
     * Récupère l'historique des transactions YellowCard pour un utilisateur au format simplifié
     * @param username Nom d'utilisateur (obligatoire pour sécurité)
     * @param fromDate Date de début (optionnel)
     * @param toDate Date de fin (optionnel)
     * @param skip Nombre de transactions à ignorer (pagination, optionnel)
     * @param limit Nombre maximum de transactions à retourner (pagination, optionnel)
     * @return Liste des transactions au format simplifié (id, status, date, amount, currency)
     */
    ResponseEntity<java.util.List<SimpleTransactionResponse>> getSimpleTransactions(
            final String username,
            final java.time.LocalDateTime fromDate,
            final java.time.LocalDateTime toDate,
            final Integer skip,
            final Integer limit);


    /**
     * Creates a collection payment.
     *
     * @param onRampRequest The request object containing on-ramp details.
     * @return ResponseEntity containing the result of the operation.
     */
    ResponseEntity<Object> createCollection(final OnRampRequest onRampRequest);

    /**
     * Creates an off-ramp payment.
     *
     * @param offRampRequest The request object containing off-ramp details.
     * @return ResponseEntity containing the result of the operation.
     */
    ResponseEntity<Object> createOffRampPaiements(final OffRampRequest offRampRequest,
                                                           String headerPin, String username);

    /**
     * Creates a collection payment for a permanent link session using the CREATE2 wallet address.
     * Unlike createCollection(), this method does not look up the user's personal wallet —
     * it uses the provided create2WalletAddress for settlement and creatorUserId as customerUID.
     *
     * @param request             The OnRampRequest from the frontend (channel, source, recipient, amount, currency, country)
     * @param create2WalletAddress The CREATE2 wallet address of the permanent link session
     * @param creatorUserId       The userId of the permanent link creator (customerUID)
     * @return ResponseEntity containing the result with transactionId and redirectUrl
     */
    ResponseEntity<Object> createCollectionForPermanentLink(OnRampRequest request,
                                                            String create2WalletAddress,
                                                            String creatorUserId);

    /**
     * Creates a web payment collection (for payment links, inspired by Djamo Pay flow).
     * The payer is not necessarily a registered user.
     *
     * @param request The web payment initiation request
     * @param creatorWalletAddress The wallet address of the payment link creator
     * @param creatorUserId The user ID of the payment link creator
     * @return ResponseEntity containing the redirect URL and transaction details
     */
    ResponseEntity<Object> createWebPaymentCollection(WebPaymentInitRequest request,
                                                      String creatorWalletAddress,
                                                      String creatorUserId);

    /**
     * Retrieves a list of available channels.
     *
     * @return ResponseEntity containing a list of ChannelDto objects.
     */
    ResponseEntity<String> getChannels(String countryCode);

    ResponseEntity<String> getRates(String currencyCode);

    /**
     * Retrieves rates for a specific channel and currency.
     * 
     * @param channelId The channel ID
     * @param currencyCode The currency code (e.g., "XAF", "XOF")
     * @return ResponseEntity containing the rates JSON with buy and sell rates
     */
    ResponseEntity<String> getRatesByChannelId(String channelId, String currencyCode);

    /**
     * Retrieves a list of available networks.
     *
     * @return ResponseEntity containing a list of NetworksDto objects.
     */
    ResponseEntity<String> getNetworks(String countryCode);

    /**
     * Estimates fees for an OnRamp operation.
     * This method attempts to call YellowCard API endpoints to get fee estimates.
     * Possible endpoints: /quote, /pricing, /estimate, or checking /collections response.
     *
     * @param amount The amount to estimate fees for
     * @param currency The currency code (e.g., "XOF")
     * @param countryCode The country code (e.g., "CI")
     * @param channelId The channel ID (optional)
     * @return ResponseEntity containing fee estimate information, or null if not available
     */
    ResponseEntity<Object> estimateOnRampFees(Double amount, String currency, String countryCode, String channelId);

    /**
     * Quote widget Yellow Card (taux, frais, montant net) — POST /business/widget/quote.
     */
    ResponseEntity<String> getWidgetQuote(YellowCardWidgetQuoteRequest request);
}
