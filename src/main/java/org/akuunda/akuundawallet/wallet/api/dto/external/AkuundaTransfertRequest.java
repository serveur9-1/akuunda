package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour effectuer un transfert compte a compte")
public class AkuundaTransfertRequest {
    
    @JsonProperty("sourceUsername")
    @NotBlank(message = "le username de la source est obligatoire")
    @Schema(description = "username de celui qui transfere l'argent", example = "00xxxxxxxxxxxx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceUsername;

    @JsonProperty("cibleUsername")
    @NotBlank(message = "le username de la cible est obligatoire")
    @Schema(description = "username de celui qui recoit le transfert d'argent", example = "00xxxxxxxxxxxx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cibleUsername;
    
    @JsonProperty("amount")
    @JsonAlias({"amount", "amount"})
    @NotNull(message = "Le montant de la transaction est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(description = "Montant de la transaction", example = "100.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;
    
    @JsonProperty("devise")
    @JsonAlias({"devise", "devise"})
    @NotBlank(message = "la devise est obligatoire")
    @Schema(description = "la devise de base du montant ", example = "XOF", requiredMode = Schema.RequiredMode.REQUIRED)
    private String devise;

    @JsonProperty("headerPin")
    @JsonAlias({"headerPin", "headerPin"})
    @NotBlank(message = "le headerPin est obligatoire")
    @Schema(description = "le header pin permettant le transfert", example = "xxxxx:xxxxxxxxx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String headerPin;

}

