package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Mise à jour des paramètres de pre-fill et provider d'un lien permanent")
public class UpdatePermanentLinkSettingsRequest {

    @Schema(description = "Code pays ISO 2 lettres pré-sélectionné", example = "FR")
    private String prefillCountry;

    @Schema(description = "Email payeur pré-rempli", example = "client@example.com")
    private String prefillEmail;

    @Schema(description = "Provider Meld forcé (ex: MERCURYO). Envoyer chaîne vide pour effacer.", example = "MERCURYO")
    private String priorityProvider;

    @Schema(description = "Nom complet du payeur pré-rempli (YellowCard)", example = "Jean Dupont")
    private String prefillPayerName;

    @Schema(description = "Téléphone du payeur pré-rempli (YellowCard source + destination)", example = "+2250102030405")
    private String prefillPayerPhone;
}
