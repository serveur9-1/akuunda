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
@Schema(description = "Réponse de création de session Kyrrex")
public class KyrrexSessionLoginResponse {

    @JsonProperty("access_key")
    @Schema(description = "Clé d'accès de la session")
    private String accessKey;

    @JsonProperty("secret_key")
    @Schema(description = "Clé secrète de la session")
    private String secretKey;

    @JsonProperty("created_at")
    @Schema(description = "Date de création de la session")
    private String createdAt;

    @JsonProperty("expire_at")
    @Schema(description = "Date d'expiration de la session")
    private String expireAt;

    @JsonProperty("tag_list")
    @Schema(description = "Liste de tags")
    private String tagList;

    @JsonProperty("updated_at")
    @Schema(description = "Date de mise à jour")
    private String updatedAt;
}
