package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour créer un lien de paiement")
public class CreatePaymentLinkRequest {

    @NotBlank(message = "La description est obligatoire")
    @Schema(
            description = "Description/libellé du paiement (ex: 'Paiement facture électricité', 'Cagnotte anniversaire')",
            example = "Paiement facture électricité",
            required = true
    )
    private String description;

    @Schema(
            description = "Montant fixe du paiement. Si null ou omis, le montant sera libre (le payeur pourra choisir le montant lors du paiement)",
            example = "5000.0",
            required = false
    )
    @Positive(message = "Le montant doit être positif")
    private Double amount;

    @Schema(
            description = "Code devise ISO (ex: XOF, EUR, USD). Si null ou omis, la devise sera choisie par le payeur lors du paiement",
            example = "XOF",
            required = false
    )
    private String currency;

    @Schema(
            description = "Date d'expiration du lien (format ISO-8601). Si null, le lien n'expire jamais. Doit être dans le futur si fourni.",
            example = "2025-12-31T23:59:59",
            required = false
    )
    private LocalDateTime expiresAt;
}

