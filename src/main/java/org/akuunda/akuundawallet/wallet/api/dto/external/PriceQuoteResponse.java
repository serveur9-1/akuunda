package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Réponse contenant l'estimation de prix et les détails de conversion")
public class PriceQuoteResponse {
    
    @Schema(description = "Devise source", example = "EUR")
    @JsonProperty("sourceCurrency")
    private String sourceCurrency;
    
    @Schema(description = "Devise destination", example = "USDC")
    @JsonProperty("destCurrency")
    private String destCurrency;
    
    @Schema(description = "Montant source", example = "100.0")
    @JsonProperty("sourceAmount")
    private Double sourceAmount;
    
    @Schema(description = "Montant destination (montant reçu après conversion)", example = "108.5")
    @JsonProperty("destAmount")
    private Double destAmount;
    
    @Schema(description = "Réseau source", example = "fiat")
    @JsonProperty("sourceNetwork")
    private String sourceNetwork;
    
    @Schema(description = "Réseau destination", example = "matic_mainnet")
    @JsonProperty("destNetwork")
    private String destNetwork;
    
    @Schema(description = "Taux de change (calculé: destAmount / sourceAmount)", example = "1.085")
    @JsonProperty(value = "exchangeRate", required = false)
    private Double exchangeRate;
    
    @Schema(description = "Frais de conversion (somme de networkFee + fixFee)", example = "2.5")
    @JsonDeserialize(using = FeesDeserializer.class)
    @JsonProperty(value = "fees", required = false)
    private Double fees;
    
    @Schema(description = "Devise des frais (identique à sourceCurrency)", example = "EUR")
    @JsonProperty(value = "feesCurrency", required = false)
    private String feesCurrency;
    
    /**
     * Calcule le taux de change et définit la devise des frais après la désérialisation
     */
    public void calculateDerivedFields() {
        // Calculer le taux de change si sourceAmount et destAmount sont disponibles
        if (sourceAmount != null && sourceAmount > 0 && destAmount != null && destAmount > 0) {
            this.exchangeRate = destAmount / sourceAmount;
        }
        
        // Définir feesCurrency à partir de sourceCurrency
        if (feesCurrency == null && sourceCurrency != null) {
            this.feesCurrency = sourceCurrency;
        }
    }
}

