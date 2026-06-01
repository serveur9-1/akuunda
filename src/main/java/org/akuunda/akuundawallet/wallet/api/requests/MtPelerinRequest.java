package org.akuunda.akuundawallet.wallet.api.requests;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour générer une signature MT Pelerin")
public class MtPelerinRequest {
    
    @JsonProperty("username")
    @JsonAlias({"username", "userName", "user_name"})
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Schema(description = "Nom d'utilisateur", example = "002250777832982", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @JsonProperty("signingPinId")
    @JsonAlias({"signingPinId", "signing_pin_id"})
    @NotBlank(message = "L'ID de la méthode de signature (signingPinId) est obligatoire")
    @Schema(description = "ID de la méthode de signature (signingPinId)", example = "b4f6c2e9-9c32-4a13-bb57-12df4cd9a77a", requiredMode = Schema.RequiredMode.REQUIRED)
    private String signingPinId;
}
