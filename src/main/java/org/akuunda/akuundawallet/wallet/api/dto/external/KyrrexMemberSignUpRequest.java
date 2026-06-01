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
@Schema(description = "Requête d'inscription d'un membre chez Kyrrex")
public class KyrrexMemberSignUpRequest {

    @Schema(description = "Email du membre", example = "david.kouassi@akuunda-pay.io", required = true)
    private String email;

    @Schema(description = "Type de membre : personal ou business", example = "personal", required = true, allowableValues = {"personal", "business"})
    private String type;

    @JsonProperty("country_id")
    @Schema(description = "ID du pays Kyrrex (récupéré via GET /kyrrex/tools/countries)", example = "972", required = true)
    private Integer countryId;
}
