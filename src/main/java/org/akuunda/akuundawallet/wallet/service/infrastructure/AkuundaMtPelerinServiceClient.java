package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface AkuundaMtPelerinServiceClient {

    ResponseEntity<String> ibanActivation(final String username);
    ResponseEntity<WalletSignatureResponse> isSignedWallet(final String username);

    ResponseEntity<MtPelerinResponse> generateSignature(String userName, String SigningPinId);

    ResponseEntity<String> procedToSignature(String userName, String otpCode);

    /**
     * Récupère une estimation de prix (quote) pour une conversion de devise
     * @param request Requête contenant les informations de conversion
     * @return Réponse avec les détails de la conversion et les frais
     */
    ResponseEntity<PriceQuoteResponse> getPriceQuote(PriceQuoteRequest request);

    /**
     * Récupère les transactions du marchand depuis MT Pelerin
     * @param fromDate Date de début
     * @param toDate Date de fin
     * @param skip Nombre de transactions à ignorer (pagination)
     * @param limit Nombre maximum de transactions à retourner
     * @param username Nom d'utilisateur pour filtrer les transactions (seules les transactions dont merchant_oid commence par {username}- sont retournées)
     * @return Réponse contenant la liste des transactions filtrées par utilisateur
     */
    ResponseEntity<MerchantTransactionResponse> getMerchantTransactions(
            LocalDateTime fromDate, LocalDateTime toDate, Integer skip, Integer limit, String username);

    /**
     * Récupère les transactions du marchand depuis MT Pelerin au format simplifié
     * @param fromDate Date de début
     * @param toDate Date de fin
     * @param skip Nombre de transactions à ignorer (pagination)
     * @param limit Nombre maximum de transactions à retourner
     * @param username Nom d'utilisateur pour filtrer les transactions
     * @return Liste des transactions au format simplifié (id, status, date, amount, currency)
     */
    ResponseEntity<List<SimpleTransactionResponse>> getSimpleMerchantTransactions(
            LocalDateTime fromDate, LocalDateTime toDate, Integer skip, Integer limit, String username);

    /**
     * Construit le lien de retrait (OffRamp) MT Pelerin
     * @param request Requête contenant les paramètres du front
     * @param username Nom d'utilisateur (optionnel, pour générer l'orderId)
     * @return Réponse contenant l'URL de redirection
     */
    ResponseEntity<MtPelerinOffRampResponse> buildOffRampLink(MtPelerinOffRampRequest request, String username);

    /**
     * Construit le lien de dépôt (OnRamp) MT Pelerin
     * @param request Requête contenant les paramètres du front
     * @param username Nom d'utilisateur (optionnel, pour générer l'orderId)
     * @return Réponse contenant l'URL de redirection
     */
    ResponseEntity<MtPelerinOnRampResponse> buildOnRampLink(MtPelerinOnRampRequest request, String username);

    ResponseEntity<TransactionStatusResponse> getTransactionById(String transactionId, String username);

    /** Signe le wallet (nouveau ou réutilise l'existant) et retourne l'adresse. */
    ResponseEntity<MtPelerinSignWalletResponse> signWallet(String username, String pinCode);
}
