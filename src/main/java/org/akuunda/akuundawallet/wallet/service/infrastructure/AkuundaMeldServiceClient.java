package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Validated
public interface AkuundaMeldServiceClient {

    ResponseEntity<MeldQuoteResponse> getMeldQuotes(
            String username, String sourceCurrency, String destCurrency,
            String amount, String countryCode
    );

    /** Variante avec filtre provider (ex. "MERCURYO"). */
    ResponseEntity<MeldQuoteResponse> getMeldQuotes(
            String username, String sourceCurrency, String destCurrency,
            String amount, String countryCode, String serviceProvider
    );

    /**
     * Crée une session widget Meld pour achat/vente de crypto.
     *
     * @param username        Identifiant de l'utilisateur
     * @param provider        Provider de paiement (ex: TRANSAK, UNLIMIT)
     * @param sourceCurrency  Devise source (ex: EUR)
     * @param destCurrency    Devise destination (ex: USDC)
     * @param sourceAmount    Montant source
     * @param sessionType     Type de session (BUY ou SELL)
     * @param countryCode     Code pays (ex: FR)
     * @param walletAddress   Adresse wallet (optionnel, pour one-time payment links)
     * @param clientIpAddress Adresse IP du client (requis pour UNLIMIT)
     * @return Réponse avec l'URL du widget Meld
     */
    /**
     * Variante historique : conserve {@code externalCustomerId == username}. Conservée pour
     * les flux internes qui n'ont pas besoin de dissocier l'identité marchand de l'identité
     * payeur (offramp, transferts wallet → wallet, etc.).
     */
    default ResponseEntity<MeldSessionResponse> createMeldSession(
            String username, String provider, String sourceCurrency,
            String destCurrency, String sourceAmount, String sessionType,
            String countryCode, String walletAddress, String clientIpAddress
    ) {
        return createMeldSession(username, null, provider, sourceCurrency, destCurrency,
                sourceAmount, sessionType, countryCode, walletAddress, clientIpAddress);
    }

    /**
     * Crée une session widget Meld en dissociant l'identité marchand (utilisée pour
     * résoudre le wallet Akuunda) de l'identifiant transmis à Meld comme
     * {@code externalCustomerId}.
     *
     * <p>Utilisé sur les flux checkout publics : le marchand identifie le wallet de
     * destination, mais Meld doit recevoir un identifiant <em>payeur</em> (email du
     * client final ou, à défaut, email marchand) — pas un login interne Akuunda.</p>
     *
     * @param username           identifie le marchand côté Akuunda (lookup wallet, suivi
     *                           opérations en BDD).
     * @param externalCustomerId valeur transmise à Meld comme {@code externalCustomerId}
     *                           (typiquement un email). Si {@code null}/vide, on retombe
     *                           sur {@code username} (rétro-compatibilité).
     */
    ResponseEntity<MeldSessionResponse> createMeldSession(
            String username, String externalCustomerId, String provider, String sourceCurrency,
            String destCurrency, String sourceAmount, String sessionType,
            String countryCode, String walletAddress, String clientIpAddress
    );

    ResponseEntity<List<MeldCountryDefaults>> getCountryDefaults(String countryCode);

    ResponseEntity<List<MeldFiatCurrency>> getFiatCurrencies(String countryCode);

    ResponseEntity<List<MeldPaymentMethod>> getPaymentMethods(String fiatCurrency);

    ResponseEntity<MeldCryptoCurrenciesResponse> getCryptoCurrencies(String countryCode);

    ResponseEntity<List<MeldPurchaseLimit>> getPurchaseLimits();

    ResponseEntity<MeldTransactionDetails> getTransactionDetails(String transactionId);

    /**
     * Rechercher des transactions Meld avec filtres.
     */
    ResponseEntity<List<MeldTransactionDetails>> searchTransactions(
            String statuses, String externalCustomerIds, Integer limit
    );

    /**
     * Récupérer une transaction Meld par sessionId (offramp).
     */
    ResponseEntity<MeldTransactionDetails> getTransactionBySessionId(String sessionId);
}
