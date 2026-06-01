package org.akuunda.akuundawallet.keycloak.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "Commande pour créer un utilisateur entreprise")
public class AddUserEntCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 5945124404545966197L;

    @NotBlank
    @Schema(description = "Nom d'utilisateur", example = "002250759146841", required = true)
    private String username;

    @Schema(description = "Prénom de l'utilisateur", example = "kone")
    private String firstName;

    @Schema(description = "Nom de l'utilisateur", example = "souleymane")
    private String lastName;
    
    @NotBlank
    @Schema(description = "Code pays (ISO 3166-1 alpha-2)", example = "CI", required = true)
    private String countryCode;
    
    @Schema(description = "Adresse email (optionnel)", example = "enterprise@example.com")
    private String email;
    
    @NotBlank
    @Schema(description = "Numéro de téléphone mobile", example = "002250777832982", required = true)
    private String mobilePhone;
    
    @Schema(description = "Numéro SIRET (optionnel)", example = "12345678901234")
    private String siret;
    
    @NotBlank
    @Schema(description = "Code PIN (6 caractères). Obligatoire pour créer l'utilisateur chez Venly", example = "123456", required = true)
    private String pinCode;
    
    @Schema(description = "Date de création de l'entreprise (optionnel)", example = "2024-01-01")
    private String dateCreation;
    
    @Schema(description = "Raison sociale de l'entreprise (optionnel)", example = "Ma Société")
    private String raisonSociale;
    
    @Schema(description = "Adresse de l'entreprise (optionnel)", example = "123 Rue Example")
    private String adresse;

    @Schema(description = "Acceptation des CGU", example = "true")
    private boolean cguAcceptation;
    
    /**
     * Code emergency partiel (5 caractères). Facultatif.
     * Si fourni et non vide, un emergency code sera créé lors de l'enregistrement.
     * Si null ou chaîne vide, aucun emergency code ne sera créé.
     */
    @Schema(description = "Code emergency partiel (5 caractères choisis par le client). Facultatif. " +
            "Si fourni et non vide, un emergency code sera créé lors de l'enregistrement. " +
            "Si null ou vide, aucun emergency code ne sera créé (pourra être créé plus tard via /emergency-code/define).",
            example = "ABC12", required = false)
    private String partialEmergencyCode;
}
