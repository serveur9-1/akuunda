package org.akuunda.akuundawallet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Demande de remboursement d'un paiement.
 * Si `amount` est omis, le remboursement est total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Demande de remboursement")
public class RefundRequest {

    @Schema(description = "Montant à rembourser (par défaut : total restant)", example = "15000")
    private Double amount;

    @Schema(description = "Raison du remboursement", example = "Produit retourné")
    private String reason;
}
