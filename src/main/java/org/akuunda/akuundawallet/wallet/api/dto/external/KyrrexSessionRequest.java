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
@Schema(description = "Requête de création de session Kyrrex")
public class KyrrexSessionRequest {

    @Schema(description = "Email du compte Kyrrex", example = "business@example.com")
    private String email;

    @Schema(description = "Mot de passe du compte Kyrrex", example = "secret")
    private String password;
}
