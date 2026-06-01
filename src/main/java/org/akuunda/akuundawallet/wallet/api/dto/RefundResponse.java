package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse du remboursement d'un lien de paiement unique (one-time)")
public class RefundResponse {

    @Schema(description = "Indique si le remboursement a réussi", example = "true")
    private Boolean success;

    @Schema(description = "Code unique du lien remboursé", example = "euv57yko")
    private String uniqueCode;

    @Schema(description = "Hash de la transaction de remboursement", example = "0xabc123...")
    private String refundTxHash;

    @Schema(description = "Adresse du wallet ayant reçu le remboursement", example = "0xB6f7b717403B9d07b582a741a1689d1aAFF6957C")
    private String refundToAddress;

    @Schema(description = "Nouveau statut du lien", example = "REFUNDED")
    private String status;

    @Schema(description = "Message d'erreur en cas d'échec")
    private String error;
}
