package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO wrapper pour les réponses d'historique de transactions.
 * Format standard avec status, message et data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse standard pour l'historique des transactions")
public class TransactionHistoryResponse {
    
    @Schema(description = "Statut de la réponse (success ou error)", 
            example = "success", required = true)
    private String status;
    
    @Schema(description = "Message descriptif de la réponse", 
            example = "Transactions récupérées avec succès", required = true)
    private String message;
    
    @Schema(description = "Liste des transactions au format simplifié", 
            required = true)
    private List<SimpleTransactionResponse> data;
}
