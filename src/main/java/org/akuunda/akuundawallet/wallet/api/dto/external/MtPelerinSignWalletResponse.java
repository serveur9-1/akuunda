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
@Schema(description = "Résultat de la signature du wallet MT Pelerin")
public class MtPelerinSignWalletResponse {

    @Schema(description = "true si le wallet est signé (nouveau ou existant)")
    private boolean signed;

    @Schema(description = "Adresse publique du wallet")
    private String walletAddress;

    @Schema(description = "Message contextuel")
    private String message;
}
