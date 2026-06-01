package org.akuunda.akuundawallet.wallet.api.dto.external;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WidgetSessionData {

    @Schema(description = """
            Optionnel. Pour les sessions de lien permanent, le serveur impose le username du marchand propriétaire
            du lien (voir merchantUsername sur GET /permanent-links/session/{code}). Fournir la même valeur aide
            au débogage ; les intégrations doivent s'appuyer sur merchantUsername retourné par l'API plutôt que sur le slug.""")
    private String userName;

    /**
     * Email du <strong>payeur</strong> (le client final qui ouvre la page de checkout).
     * Transmis à Meld comme {@code externalCustomerId} ; permet au provider sous-jacent
     * (Mercuryo, Transak…) de pré-remplir son widget et de tracer la session côté provider.
     *
     * <p>Optionnel : si absent, le serveur retombe sur l'email du marchand propriétaire du
     * lien (puis sur son login Akuunda si pas d'email renseigné).</p>
     */
    @Schema(description = "Email du client final (payeur) — transmis à Meld comme externalCustomerId. "
            + "Optionnel : fallback sur l'email marchand si absent.",
            example = "client@example.com",
            nullable = true)
    private String customerEmail;

    private String countryCode;
    private String sourceCurrencyCode;
    private String sourceAmount;
    private String destinationCurrencyCode;

    @Schema(description = "Provider Meld sous-jacent. Défaut serveur : `MERCURYO`. "
            + "Autres valeurs courantes : `TRANSAK`, `UNLIMIT`.",
            example = "MERCURYO",
            defaultValue = "MERCURYO",
            nullable = true)
    private String serviceProvider;

    private String sessionType;

    @Schema(description = "Optional wallet address override. If provided, USDC will be sent to this address instead of the user's personal wallet. Used for one-time payment links (create2WalletAddress).")
    private String walletAddress;

    @Schema(description = "Client IP address. Required for UNLIMIT provider. The Unlimit widget will only open on this IP address.")
    private String clientIpAddress;
}
