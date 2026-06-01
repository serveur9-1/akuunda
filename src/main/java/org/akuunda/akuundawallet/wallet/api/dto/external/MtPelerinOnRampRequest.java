package org.akuunda.akuundawallet.wallet.api.dto.external;

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
@Schema(description = "Requête pour créer un lien de dépôt (OnRamp) MT Pelerin")
public class MtPelerinOnRampRequest {
    
    @NotBlank(message = "La devise fiat est obligatoire")
    @Schema(description = "Devise fiat sélectionnée par l'utilisateur", example = "EUR", required = true)
    private String bsc; // fiat sélectionnée par l'utilisateur
    
    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(description = "Montant fiat", example = "100.0", required = true)
    private Double bsa; // montant fiat
    
    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Schema(description = "Numéro de téléphone pour login pré-rempli", example = "33612108828", required = true)
    private String phone; // login pré-rempli
    
    @NotBlank(message = "Le code pays est obligatoire")
    @Schema(description = "Code pays", example = "FR", required = true)
    private String ctry; // pays
    
    @NotBlank(message = "La langue est obligatoire")
    @Schema(description = "Langue du widget", example = "fr", required = true)
    private String lang; // langue du widget
    
    @NotBlank(message = "L'adresse du wallet est obligatoire")
    @Schema(description = "Adresse du wallet utilisateur", example = "0x090d3840A4eD99ED40Be741884b7f941A856A23a", required = true)
    private String addr; // wallet utilisateur
    
    @Schema(description = "Code de validation (si utilisé)", example = "1234", required = false)
    private String code; // code de validation (si tu l'utilises)
    
    @Schema(description = "Signature optionnelle", example = "", required = false)
    private String hash; // signature optionnelle

    @Schema(description = "Code PIN de l'utilisateur pour signer le wallet à la volée (si pas encore signé)", example = "1234", required = false)
    private String pinCode;
}

