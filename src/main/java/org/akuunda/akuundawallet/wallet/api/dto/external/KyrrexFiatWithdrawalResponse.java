package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Réponse de retrait fiat Kyrrex")
public class KyrrexFiatWithdrawalResponse {

    @JsonProperty("uid")
    @Schema(description = "Identifiant")
    private String id;

    @Schema(description = "Statut")
    private String status;

    @Schema(description = "Montant")
    private String amount;

    @Schema(description = "Devise")
    private String currency;

    @Schema(description = "Méthode de payout")
    private String payoutMethod;

    @Schema(description = "Date de création")
    private String createdAt;
}
