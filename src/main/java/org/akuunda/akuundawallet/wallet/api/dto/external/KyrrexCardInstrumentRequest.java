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
@Schema(description = "Requête de création d'un instrument carte Kyrrex")
public class KyrrexCardInstrumentRequest {

    @Schema(description = "Type d'instrument (ex: card_s)", example = "card_s")
    private String instrument;
}
