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
@Schema(description = "Requête de création d'adresse de dépôt Kyrrex")
public class KyrrexDepositAddressRequest {

    @Schema(description = "Identifiant de la chaîne de dépôt (dchain)", example = "btc")
    private String dchain;

    @Schema(description = "Nom de l'adresse de dépôt", example = "btc")
    private String name;
}
