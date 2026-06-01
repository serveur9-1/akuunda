package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Provider fiat Kyrrex")
public class KyrrexProviderResponse {

    @JsonProperty("provider_id")
    @Schema(description = "Identifiant du provider")
    private String providerId;

    @JsonProperty("name")
    @Schema(description = "Nom du provider")
    private String name;

    @JsonProperty("currencies")
    @Schema(description = "Devises supportées")
    private List<String> currencies;

    @Schema(description = "Catégorie du provider (ex: card, bank_transfer)")
    private String category;

    @Schema(description = "Instrument du provider (ex: card_s, sepa, sepa_iframe)")
    private String instrument;
}
