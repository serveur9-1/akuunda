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
@Schema(description = "Estimation d'échange avancé Kyrrex (On-Ramp)")
public class KyrrexAdvancedExchangeEstimateResponse {

    @JsonProperty("crypto_amount")
    @Schema(description = "Montant en crypto")
    private BigDecimal cryptoAmount;

    @Schema(description = "Montant en fiat")
    private BigDecimal fiatAmount;

    @Schema(description = "Taux de change")
    private BigDecimal rate;

    @Schema(description = "Frais")
    private BigDecimal fee;

    @Schema(description = "Commission du provider")
    private BigDecimal providerCommission;

    @Schema(description = "Devise fiat")
    private String fiatCurrency;

    @Schema(description = "Asset crypto")
    private String cryptoAsset;
}
