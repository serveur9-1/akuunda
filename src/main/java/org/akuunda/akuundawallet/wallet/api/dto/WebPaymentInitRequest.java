package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour initier un paiement web via YellowCard OnRamp")
public class WebPaymentInitRequest {

    @NotBlank(message = "Le code unique du lien est obligatoire")
    @Schema(
            description = "Code unique du lien de paiement",
            example = "gn1mb",
            required = true
    )
    private String uniqueCode;

    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(
            description = "Montant à payer",
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

    @NotBlank(message = "Le numéro de téléphone du payeur est obligatoire")
    @Schema(
            description = "Numéro de téléphone du payeur (format international, ex: +2250700123456)",
            example = "+2250700123456",
            required = true
    )
    private String phoneNumber;

    @NotBlank(message = "Le code pays est obligatoire")
    @Schema(
            description = "Code pays ISO (ex: CI, SN, ML)",
            example = "CI",
            required = true
    )
    private String countryCode;

    @NotBlank(message = "L'opérateur mobile money est obligatoire")
    @Schema(
            description = "ID de l'opérateur mobile money (networkId)",
            example = "8d18204e-b51f-4554-815d-71586d0dac13",
            required = true
    )
    private String operatorId;

    @Schema(
            description = "Nom complet du payeur",
            example = "Jean Dupont",
            required = false
    )
    private String payerName;

    @Schema(
            description = "Email du payeur (optionnel)",
            example = "jean.dupont@example.com",
            required = false
    )
    private String payerEmail;

    @Schema(
            description = "Adresse du payeur (optionnel)",
            example = "Abidjan, Cocody",
            required = false
    )
    private String payerAddress;

    @Schema(
            description = "Date de naissance du payeur (format MM/DD/YYYY, optionnel)",
            example = "01/01/1990",
            required = false
    )
    private String payerDob;
}


