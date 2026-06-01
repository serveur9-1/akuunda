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
@Schema(description = "Pays disponible chez Kyrrex")
public class KyrrexCountryResponse {

    @Schema(description = "ID du pays chez Kyrrex", example = "972")
    private Integer id;

    @Schema(description = "Code ISO 2 lettres", example = "FR")
    private String code;

    @JsonProperty("eng_name")
    @Schema(description = "Nom du pays en anglais", example = "France")
    private String engName;

    @Schema(description = "Code ISO numérique", example = "250")
    private Integer iso;

    @Schema(description = "Région/location", example = "Other")
    private String location;
}
