package org.akuunda.akuundawallet.wallet.api.dto.external;

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
@Schema(description = "Requête pour créer un lien de retrait (OffRamp) MT Pelerin")
public class MtPelerinOffRampRequest {
    
    @NotBlank(message = "La devise fiat est obligatoire")
    @Schema(description = "Devise fiat envoyée par le marchand (ex: EUR)", example = "EUR", required = true)
    @JsonProperty("sdc")
    private String sdc; // fiat envoyée par le marchand
    
    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(description = "Montant en devise fiat (sdc) que l'utilisateur souhaite recevoir (ex: 100 EUR). " +
            "Le back-end appelle /price-quote pour convertir en USDC et envoie le montant USDC à MT Pelerin (pas d'envoi en euro).", example = "100.0", required = true)
    @JsonProperty("sda")
    private Double sda; // montant en fiat (sdc) que l'utilisateur souhaite recevoir ; le back convertit en USDC via getPriceQuote
    
    @NotBlank(message = "La langue est obligatoire")
    @Schema(description = "Langue du widget", example = "fr", required = true)
    @JsonProperty("lang")
    private String lang; // langue du widget
    
    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Schema(description = "Numéro de téléphone pour login pré-rempli", example = "33612108828", required = true)
    @JsonProperty("phone")
    private String phone; // login pré-rempli
    
    @NotBlank(message = "Le code pays est obligatoire")
    @Schema(description = "Code pays pré-rempli à la connexion", example = "FR", required = true)
    @JsonProperty("ctry")
    private String ctry; // pays pré-rempli à la connexion

    @Schema(description = "Code PIN de l'utilisateur pour signer le wallet à la volée (si pas encore signé)", example = "1234", required = false)
    private String pinCode;
}

