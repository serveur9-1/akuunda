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
@Schema(description = "Réponse de confirmation de paiement YellowCard (étape 2 — appel YellowCard effectué)")
public class ConfirmPayYcResponse {

    @Schema(description = "Code unique du lien")
    private String uniqueCode;

    @Schema(description = "Statut du lien après confirmation. 'PENDING' si YellowCard a été appelé avec succès")
    private String status;

    @Schema(description = "ID de la transaction YellowCard (sequenceId)")
    private String yellowCardTransactionId;

    @Schema(description = "Méthode de paiement détectée: 'push' (MoMo), 'link' (Wave/Orange), 'bank' (virement)")
    private String paymentMethod;

    @Schema(description = "URL de redirection YellowCard pour paiement Wave/Orange Money (si disponible)")
    private String redirectUrl;

    @Schema(description = "Informations bancaires pour virement (si disponible)")
    private YcBankInfoDto bankInfo;

    @Schema(description = "Nom du réseau de paiement (ex: MTN Mobile Money, Wave)")
    private String networkName;

    @Schema(description = "Montant converti en USD")
    private Double convertedAmount;

    @Schema(description = "Devise locale du paiement (ex: XOF, GHS)")
    private String localCurrency;

    @Schema(description = "Taux de conversion utilisé")
    private Double rate;
}
