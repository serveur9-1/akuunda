package org.akuunda.akuundawallet.wallet.api.dto;

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
@Schema(description = "Requête pour calculer les frais d'une opération")
public class FeesCalculationRequest {

    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(
            description = "Montant à déposer en devise locale",
            example = "5000.0",
            required = true
    )
    private Double amount;

    @NotBlank(message = "La devise est obligatoire")
    @Schema(
            description = "Code devise ISO (ex: XOF, EUR, USD)",
            example = "XOF",
            required = true
    )
    private String currency;

    @NotBlank(message = "Le code pays est obligatoire")
    @Schema(
            description = "Code pays ISO (ex: CI, SN, FR)",
            example = "CI",
            required = true
    )
    private String countryCode;

    @Schema(
            description = "Opérateur de paiement (yellowcard, guardarian). Si non spécifié, sera détecté automatiquement selon le pays.",
            example = "yellowcard",
            required = false
    )
    private String operator;
}

