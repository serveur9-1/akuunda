package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informations publiques d'un lien de paiement (pour l'interface web)")
public class PaymentLinkInfoResponse {

    @Schema(description = "Code unique du lien", example = "gn1mb")
    private String uniqueCode;

    @Schema(description = "Description/libellé du paiement", example = "Paiement facture électricité")
    private String description;

    @Schema(description = "Montant fixe (null si montant libre)", example = "5000.0")
    private Double amount;

    @Schema(description = "Code devise (null si devise libre)", example = "XOF")
    private String currency;

    @Schema(description = "Si le lien est actif", example = "true")
    private Boolean isActive;

    @Schema(description = "Date d'expiration (null si pas d'expiration)")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Nombre total de paiements reçus", example = "5")
    private Integer totalPayments;

    @Schema(description = "Montant total reçu", example = "25000.0")
    private Double totalAmountReceived;

    @Schema(description = "Nom du créateur (si disponible)", example = "Jean Dupont")
    private String creatorName;
}


