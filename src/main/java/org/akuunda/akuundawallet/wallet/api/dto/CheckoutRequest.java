package org.akuunda.akuundawallet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête de création de checkout pour boutique en ligne")
public class CheckoutRequest {

    @NotNull(message = "Le montant est obligatoire")
    @Schema(description = "Montant total du panier", example = "15000.0", required = true)
    private Double amount;

    @NotBlank(message = "La devise est obligatoire")
    @Schema(description = "Devise du montant", example = "XOF", required = true)
    private String currency;

    @NotBlank(message = "La référence est obligatoire")
    @Schema(description = "Référence unique de la commande chez le marchand", example = "CMD-123", required = true)
    private String reference;

    @Schema(description = "Description de la commande", example = "Commande #123 - 3 articles")
    private String description;

    @Schema(description = "URL de redirection après paiement réussi", example = "https://boutique.com/payment/success")
    private String callbackUrl;

    @Schema(description = "URL de redirection si le client annule", example = "https://boutique.com/payment/cancel")
    private String cancelUrl;

    @Schema(description = "URL du webhook pour notification de paiement", example = "https://boutique.com/api/webhooks/akuunda")
    private String webhookUrl;

    @Schema(description = "Métadonnées supplémentaires (passées telles quelles dans le webhook)")
    private Map<String, String> metadata;
}
