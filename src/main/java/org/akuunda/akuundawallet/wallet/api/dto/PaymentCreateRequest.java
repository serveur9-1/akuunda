package org.akuunda.akuundawallet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Requête de création de paiement pour un marchand intégrateur.
 * Auth : Authorization: Bearer sk_live_... (ou X-API-Key)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Création d'un paiement")
public class PaymentCreateRequest {

    @NotNull
    @Positive
    @Schema(description = "Montant", example = "15000", required = true)
    private Double amount;

    @NotBlank
    @Schema(description = "Devise ISO 4217", example = "XOF", required = true)
    private String currency;

    @NotBlank
    @Schema(description = "Référence unique côté marchand", example = "CMD-123", required = true)
    private String reference;

    @Schema(description = "Description du paiement", example = "Commande #123")
    private String description;

    @Schema(description = "URL de redirection après paiement (override la valeur enregistrée sur la clé)",
            example = "https://boutique.com/success")
    private String returnUrl;

    @Schema(description = "URL de redirection si l'utilisateur annule (override)",
            example = "https://boutique.com/cancel")
    private String cancelUrl;

    @Schema(description = "URL de webhook pour notification (override)",
            example = "https://boutique.com/api/webhooks/akuunda")
    private String webhookUrl;

    @Schema(description = "Métadonnées libres, restituées telles quelles dans le webhook")
    private Map<String, String> metadata;

    @Schema(
        description = "Pays de paiement cible (ISO 3166-1 alpha-2). Si fourni, la page de " +
                      "paiement sélectionne ce pays automatiquement sans interaction de l'acheteur. " +
                      "Idéal pour les boutiques et billetteries à marché local.",
        example = "CI"
    )
    private String paymentCountry;
}
