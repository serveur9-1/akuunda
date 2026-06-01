package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour obtenir une estimation de prix (quote) depuis MT Pelerin")
public class PriceQuoteRequest {
    
    @JsonProperty("sourceCurrency")
    @JsonAlias({"sourceCurrency", "source_currency"})
    @NotBlank(message = "La devise source est obligatoire")
    @Schema(description = "Devise source (ex: EUR, USD)", example = "EUR", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceCurrency;
    
    @JsonProperty("destCurrency")
    @JsonAlias({"destCurrency", "dest_currency"})
    @NotBlank(message = "La devise destination est obligatoire")
    @Schema(description = "Devise destination (ex: USDC, BTC)", example = "USDC", requiredMode = Schema.RequiredMode.REQUIRED)
    private String destCurrency;
    
    @JsonProperty("sourceAmount")
    @JsonAlias({"sourceAmount", "source_amount"})
    @NotNull(message = "Le montant source est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(description = "Montant à convertir", example = "100.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double sourceAmount;
    
    @JsonProperty("sourceNetwork")
    @JsonAlias({"sourceNetwork", "source_network"})
    @NotBlank(message = "Le réseau source est obligatoire")
    @Schema(description = "Réseau source (ex: fiat, matic_mainnet, ethereum_mainnet)", example = "fiat", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceNetwork;
    
    @JsonProperty("destNetwork")
    @JsonAlias({"destNetwork", "dest_network"})
    @NotBlank(message = "Le réseau destination est obligatoire")
    @Schema(description = "Réseau destination (ex: matic_mainnet, ethereum_mainnet)", example = "matic_mainnet", requiredMode = Schema.RequiredMode.REQUIRED)
    private String destNetwork;
    
    @JsonProperty("isCardPayment")
    @JsonAlias({"isCardPayment", "is_card_payment"})
    @Schema(description = "Indique si le paiement se fait par carte", example = "false", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean isCardPayment;
}

