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
@Schema(description = "Document d'identification disponible chez Kyrrex")
public class KyrrexIdentificationDocumentResponse {

    @Schema(description = "ID du type de document", example = "1")
    private Integer id;

    @Schema(description = "Nom du type de document", example = "Passport")
    private String name;
}
