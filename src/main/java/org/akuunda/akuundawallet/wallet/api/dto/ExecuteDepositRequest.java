package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour exécuter le dépôt blockchain (Client → Intermédiaire → Smart contract)")
public class ExecuteDepositRequest {

    @NotBlank(message = "Le PIN du client est obligatoire pour signer le transfert")
    @Schema(description = "PIN du client (format Venly, ex: PIN:123456)", example = "PIN:123456")
    private String clientPin;
}
