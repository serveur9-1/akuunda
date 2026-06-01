package org.akuunda.akuundawallet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class TransactionStatusRequest {
    @NotBlank(message = "Le nom d'utilisateur est obligatoire.")
    private String username;

    @NotBlank(message = "L'ID de la transaction est obligatoire.")
    private String transactionId;

    @NotBlank(message = "Le type de transaction est obligatoire.")
    @Schema(
            description = "Type de transaction. Les valeurs possibles sont : MTPELERIN, YELLOWCARD, GUARDIARAN.",
            allowableValues = {"MTPELERIN", "YELLOWCARD", "GUARDIARAN"}
    )
    private String transactionType;
}
