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
@Schema(description = "Réponse pour l'initiation d'un paiement web")
public class WebPaymentInitResponse {

    @Schema(description = "Indique si l'initiation a réussi", example = "true")
    private Boolean success;

    @Schema(description = "Message de statut", example = "Payment initiated successfully")
    private String message;

    @Schema(description = "URL de redirection YellowCard pour compléter le paiement", example = "https://yellowcard.io/payment/...")
    private String redirectUrl;

    @Schema(description = "ID de transaction YellowCard", example = "tx_1234567890")
    private String transactionId;

    @Schema(description = "Code unique du lien de paiement", example = "gn1mb")
    private String uniqueCode;

    @Schema(description = "Montant du paiement", example = "5000.0")
    private Double amount;

    @Schema(description = "Devise du paiement", example = "XOF")
    private String currency;
}


