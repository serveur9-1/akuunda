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
@Schema(description = "Réponse de dépôt fiat Kyrrex — l'utilisateur DOIT être redirigé vers frameUrl pour compléter le paiement")
public class KyrrexFiatDepositResponse {

    @Schema(description = "UUID du dépôt")
    private String uuid;

    @JsonProperty("frame_url")
    @Schema(description = "URL de la frame de paiement vers laquelle l'utilisateur doit être redirigé")
    private String frameUrl;
}
