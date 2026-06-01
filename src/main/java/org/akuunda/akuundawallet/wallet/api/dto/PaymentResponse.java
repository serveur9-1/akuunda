package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Réponse standard d'un paiement (création ou consultation).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Représentation d'un paiement")
public class PaymentResponse {

    @Schema(description = "Identifiant unique du paiement", example = "pay_a1b2c3d4e5f6")
    private String id;

    @Schema(description = "URL de la page de paiement hébergée à présenter au client",
            example = "https://qr.akuunda-pay.io/checkout/pay_a1b2c3d4e5f6")
    private String url;

    @Schema(description = "Mode : \"live\" ou \"test\"", example = "live")
    private String mode;

    @Schema(description = "Statut courant", example = "pending",
            allowableValues = {"pending", "paid", "expired", "cancelled", "failed", "refunded"})
    private String status;

    @Schema(description = "Montant", example = "15000")
    private Double amount;

    @Schema(description = "Devise", example = "XOF")
    private String currency;

    @Schema(description = "Référence marchand", example = "CMD-123")
    private String reference;

    @Schema(description = "Description", example = "Commande #123")
    private String description;

    @Schema(description = "Métadonnées libres (si transmises à la création)")
    private Map<String, String> metadata;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Date d'expiration de la session (1h après création)")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Date de création")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Date de paiement effectif (si status = paid)")
    private LocalDateTime paidAt;

    @Schema(description = "Montant total remboursé (si applicable)")
    private Double refundedAmount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Date du dernier remboursement")
    private LocalDateTime refundedAt;
}
