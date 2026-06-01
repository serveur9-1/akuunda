package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour annuler un paiement conditionnel")
public class CancelConditionalPaymentRequest {

    @Schema(
            description = "Raison de l'annulation",
            example = "Client n'a pas pu se présenter"
    )
    private String reason;
}

