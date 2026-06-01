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
@Schema(description = "Requête de création de réquisite Kyrrex")
public class KyrrexRequisiteRequest {

    @JsonProperty("dchain")
    @Schema(description = "Identifiant de chaîne (dchain)", example = "btc")
    private String currency;

    @Schema(description = "Adresse crypto")
    private String address;

    @Schema(description = "Libellé")
    private String label;
}
