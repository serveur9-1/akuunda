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
@Schema(description = "Réponse d'inscription d'un membre chez Kyrrex")
public class KyrrexMemberSignUpResponse {

    @Schema(description = "Identifiant unique du membre Kyrrex")
    private String uid;

    @JsonProperty("access_key")
    @Schema(description = "Clé d'accès propre à l'utilisateur")
    private String accessKey;

    @JsonProperty("secret_key")
    @Schema(description = "Clé secrète propre à l'utilisateur")
    private String secretKey;

    @Schema(description = "Email du membre")
    private String email;

    @Schema(description = "Statut du membre")
    private String status;
}
