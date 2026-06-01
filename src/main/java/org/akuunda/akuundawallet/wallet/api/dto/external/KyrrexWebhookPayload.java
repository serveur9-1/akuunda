package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Payload générique webhook Kyrrex")
public class KyrrexWebhookPayload {

    @Schema(description = "Type d'événement", example = "deposit")
    private String type;

    @Schema(description = "Données de l'événement")
    private JsonNode data;
}
