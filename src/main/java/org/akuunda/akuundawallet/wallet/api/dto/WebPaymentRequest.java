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
@Schema(description = "Requête pour effectuer un paiement via l'interface web (sans authentification)")
public class WebPaymentRequest {

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
            description = "Montant à payer (obligatoire même si le lien a un montant fixe, pour validation)",
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

    @Schema(
            description = "Numéro de téléphone du payeur (obligatoire pour mobile money, optionnel pour autres moyens de paiement)",
            example = "+2250700123456",
            required = false
    )
    private String phoneNumber;

    @NotBlank(message = "Le code pays est obligatoire")
    @Schema(
            description = "Code pays ISO (ex: CI, SN, ML)",
            example = "CI",
            required = true
    )
    private String countryCode;

    @NotBlank(message = "Le moyen de paiement (channel ID) est obligatoire")
    @Schema(
            description = "ID du canal de paiement (channel ID de YellowCard ou Guardarian). Pour mobile money, c'est le channelId. Pour carte/virement, c'est l'ID du partenaire.",
            example = "37b63794-284b-4a09-863b-9b74a3f621e1",
            required = true
    )
    private String paymentMethodId;
    
    @Schema(
            description = "Type de moyen de paiement (momo, bank, card, etc.)",
            example = "momo",
            required = false
    )
    private String paymentMethodType;

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
            description = "Message/note optionnel du payeur",
            example = "Paiement facture électricité - Janvier 2024",
            required = false
    )
    private String note;
}


