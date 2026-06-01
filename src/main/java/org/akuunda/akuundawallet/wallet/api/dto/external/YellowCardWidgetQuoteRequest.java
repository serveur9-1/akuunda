package org.akuunda.akuundawallet.wallet.api.dto.external;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Requête pour POST /business/widget/quote (Yellow Card).
 * Fournir {@code localAmount} (montant fiat saisi) ou {@code cryptoAmount} (USDC).
 */
@Data
@Schema(description = "Demande de quote widget Yellow Card (Sell / Buy)")
public class YellowCardWidgetQuoteRequest {

    @Schema(description = "Devise fiat (ex. XOF, XAF)", example = "XOF", requiredMode = Schema.RequiredMode.REQUIRED)
    private String currency;

    @Schema(description = "Montant en devise locale (off-ramp : montant que l'utilisateur souhaite retirer)")
    private Double localAmount;

    @Schema(description = "Montant crypto USDC (si déjà converti côté client)")
    private Double cryptoAmount;

    @Schema(description = "Code pays ISO-2 pour la conversion local → crypto", example = "CI")
    private String country;

    @Schema(description = "Identifiant du canal Yellow Card", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channelId;

    @Schema(description = "Crypto-monnaie", example = "USDC", defaultValue = "USDC")
    private String coin = "USDC";

    @Schema(description = "Réseau blockchain", example = "POLYGON", defaultValue = "POLYGON")
    private String network = "POLYGON";

    @Schema(description = "Type de transaction widget", example = "Sell", defaultValue = "Sell")
    private String transactionType = "Sell";

    @Schema(description = "Méthode de paiement pour repli local (momo, bank_transfer…)")
    private String paymentMethod;
}
