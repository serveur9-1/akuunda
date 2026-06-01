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
@Schema(description = "Requête pour effectuer un paiement via un lien")
public class ProcessPaymentRequest {

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

    @NotBlank(message = "Le username du payeur est obligatoire")
    @Schema(
            description = "Username (numéro de téléphone) du payeur",
            example = "002250777832982",
            required = true
    )
    private String payerUsername;

    @Schema(
            description = "Message/note optionnel du payeur",
            example = "Paiement facture électricité - Janvier 2024",
            required = false
    )
    private String note;
}

