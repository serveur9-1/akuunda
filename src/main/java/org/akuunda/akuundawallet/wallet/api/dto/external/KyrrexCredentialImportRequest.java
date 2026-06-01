package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Import des credentials Kyrrex existants (membre déjà créé chez Kyrrex)")
public class KyrrexCredentialImportRequest {

    @NotBlank
    @Schema(description = "UID membre Kyrrex", example = "mltz9a44d")
    private String uid;

    @NotBlank
    @JsonProperty("access_key")
    @Schema(description = "Clé d'accès membre Kyrrex")
    private String accessKey;

    @NotBlank
    @JsonProperty("secret_key")
    @Schema(description = "Clé secrète membre Kyrrex")
    private String secretKey;
}
