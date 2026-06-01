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
@Schema(description = "Instrument carte Kyrrex")
public class KyrrexCardInstrumentResponse {

    @Schema(description = "Identifiant unique de l'instrument")
    private Long uid;

    @Schema(description = "Type de l'instrument")
    private String type;

    @Schema(description = "Statut de l'instrument")
    private String status;

    @Schema(description = "Numéro masqué de la carte")
    private String maskedNumber;

    @Schema(description = "Date de création")
    private String createdAt;
}
