package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Réponse d'échange avancé Kyrrex (On-Ramp)")
public class KyrrexAdvancedExchangeResponse {

    @JsonProperty("uid")
    @Schema(description = "Identifiant unique de l'échange (uid Kyrrex)")
    private String uid;

    @Schema(description = "Statut")
    private String status;

    @JsonProperty("fiat_amount")
    @Schema(description = "Montant fiat")
    private BigDecimal fiatAmount;

    @JsonProperty("crypto_amount")
    @Schema(description = "Montant crypto")
    private BigDecimal cryptoAmount;

    @JsonProperty("crypto_asset")
    @Schema(description = "Asset crypto")
    private String cryptoAsset;

    @JsonProperty("fiat_currency")
    @Schema(description = "Devise fiat")
    private String fiatCurrency;

    @Schema(description = "Taux de change")
    private BigDecimal rate;

    @Schema(description = "Frais")
    private BigDecimal fee;

    @JsonProperty("created_at")
    @Schema(description = "Date de création")
    private String createdAt;

    @JsonProperty("redirect_url")
    @Schema(description = "URL de redirection pour compléter le paiement")
    private String redirectUrl;

    @JsonProperty("frame_url")
    @Schema(description = "URL de la frame pour compléter le paiement (Kyrrex On-Ramp)")
    private String frameUrl;
}
