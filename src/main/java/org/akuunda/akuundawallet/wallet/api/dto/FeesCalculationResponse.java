package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse contenant le calcul des frais et le montant net")
public class FeesCalculationResponse {

    @Schema(description = "Montant envoyé par l'utilisateur (ce qu'il paie)", example = "5000.0", required = true)
    private Double amountSent;

    @Schema(description = "Devise du montant", example = "XOF", required = true)
    private String currency;

    @Schema(description = "Frais estimés qui seront prélevés", example = "100.0", required = true)
    private Double estimatedFees;

    @Schema(description = "Montant que l'utilisateur recevra après déduction des frais", example = "4900.0", required = true)
    private Double amountReceived;

    @Schema(description = "Taux de change utilisé (ex: 1 USD = 588.96 XOF)", example = "588.96")
    private Double exchangeRate;

    @Schema(description = "Taux de change estimé (alias de exchangeRate pour compatibilité)", example = "588.96")
    private Double estimatedExchangeRate;

    @Schema(description = "Pourcentage de frais Akuunda Pay (ex: 2.0 pour 2%)", example = "2.0")
    private Double feePercentage;

    @Schema(description = "Opérateur utilisé pour le calcul", example = "yellowcard")
    private String operator;

    @Schema(description = "Détails du calcul (breakdown) - contient les taux intermédiaires")
    private FeesBreakdown breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Détails du calcul des frais")
    public static class FeesBreakdown {
        @Schema(description = "Taux Currency Freaks (XOF → USD) - Non utilisé pour YellowCard OnRamp (utilisé uniquement pour le fallback de l'affichage du solde)", example = "0.0017")
        private Double currencyFreaksRate;

        @Schema(description = "Taux YellowCard (USD → XOF)", example = "588.96")
        private Double yellowCardRate;

        @Schema(description = "Taux de frais Akuunda Pay", example = "0.02")
        private Double akuundaFeeRate;

        @Schema(description = "Montant converti en USD", example = "8.5")
        private Double amountInUsd;

        @Schema(description = "Montant après conversion YellowCard (avant frais Akuunda)", example = "5000.0")
        private Double amountAfterYellowCardRate;

        // Pour Guardarian
        @Schema(description = "Frais de service Guardarian (si applicable)", example = "0.15")
        private Double guardarianServiceFee;

        @Schema(description = "Montant converti Guardarian (si applicable)", example = "29.85")
        private Double guardarianConvertedAmount;

        @Schema(description = "Taux de change estimé Guardarian (si applicable)", example = "1.12524851")
        private Double guardarianExchangeRate;
        
        // Champs de diagnostic pour Guardarian
        @Schema(description = "Montant estimatedAmount (USDC) depuis Guardarian - pour diagnostic", example = "33.59")
        private Double guardarianEstimatedAmount;
        
        @Schema(description = "Montant convertedAmount (devise d'origine) depuis Guardarian - pour diagnostic", example = "29.85")
        private Double guardarianConvertedAmountFromAPI;
        
        @Schema(description = "Indique si convertedAmount a été utilisé pour calculer les frais - pour diagnostic", example = "true")
        private Boolean usedConvertedAmount;
    }
}

