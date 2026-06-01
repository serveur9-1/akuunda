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
@Schema(description = "Requête d'échange avancé Kyrrex (On-Ramp)")
public class KyrrexAdvancedExchangeRequest {

    @JsonProperty("deposit_address_uid")
    @Schema(description = "UID de l'adresse de dépôt")
    private String depositAddressUid;

    @JsonProperty("fiat_currency")
    @Schema(description = "Devise fiat", example = "EUR")
    private String fiatCurrency;

    @JsonProperty("crypto_asset")
    @Schema(description = "Asset crypto", example = "BTC")
    private String cryptoAsset;

    @JsonProperty("fiat_amount")
    @Schema(description = "Montant en fiat")
    private BigDecimal fiatAmount;

    @JsonProperty("payment_method")
    @Schema(description = "Méthode de paiement", example = "card_s")
    private String paymentMethod;

    @JsonProperty("provider_id")
    @Schema(description = "Identifiant du provider")
    private String providerId;
}
