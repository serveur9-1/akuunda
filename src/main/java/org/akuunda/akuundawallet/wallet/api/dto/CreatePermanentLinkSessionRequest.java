package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour créer une session de paiement sur un lien permanent")
public class CreatePermanentLinkSessionRequest {

    @NotNull(message = "Le montant est obligatoire")
    @Schema(description = "Montant à payer pour cette session", example = "25.0", required = true)
    private Double amount;

    @Schema(description = "Devise de la session (défaut: USDC)", example = "USDC", required = false)
    private String currency;

    @Schema(description = "Référence client optionnelle", example = "CMD-01", required = false)
    private String reference;

    @Schema(description = "Nom du payeur", example = "Jean Dupont", required = false)
    private String payerName;

    @Schema(description = "Téléphone du payeur", example = "+2250700123456", required = false)
    private String payerPhone;

    @Schema(description = "Email du payeur", example = "jean.dupont@example.com", required = false)
    private String payerEmail;
}
