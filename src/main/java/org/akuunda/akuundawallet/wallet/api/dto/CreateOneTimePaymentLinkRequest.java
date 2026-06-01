package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour créer un lien de paiement unique (one-time)")
public class CreateOneTimePaymentLinkRequest {

    @NotBlank(message = "La description est obligatoire")
    @Schema(description = "Description/libellé du paiement", example = "Facture n°2024-001", required = true)
    private String description;

    @Schema(description = "Montant indicatif du paiement (optionnel, non enforced on-chain)", example = "5000.0", required = false)
    private Double amount;

    @Schema(description = "Code devise ISO (ex: XOF, EUR, USD), optionnel", example = "XOF", required = false)
    private String currency;

    @Schema(
            description = "Date d'expiration du lien. Si null, expire 24h après la création.",
            example = "2025-12-31T23:59:59",
            required = false
    )
    private LocalDateTime expiresAt;
}
