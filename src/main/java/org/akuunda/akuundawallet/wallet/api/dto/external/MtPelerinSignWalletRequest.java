package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Requête pour signer le wallet MT Pelerin")
public class MtPelerinSignWalletRequest {

    @Schema(description = "Code PIN de l'utilisateur", example = "1234", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pinCode;
}
