package org.akuunda.akuundawallet.keycloak.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "Commande pour créer un utilisateur particulier")
public class AddUserPartCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 5945124404545966197L;

    @NotBlank
    @Schema(description = "Nom d'utilisateur (si facebook/google: facebookId/googleId, sinon mobilePhone)", example = "002250759146841", required = true)
    private String username;  //if facebook (facebookId, googleId) else mobilePhone
    
    @Schema(description = "Prénom de l'utilisateur", example = "kone")
    private String firstName;
    
    @Schema(description = "Nom de l'utilisateur", example = "souleymane")
    private String lastName;
    
    @NotBlank
    @Schema(description = "Code PIN (6 caractères). Obligatoire pour créer l'utilisateur chez Venly", example = "123456", required = true)
    private String pinCode;
    
    @NotBlank
    @Schema(description = "Code pays (ISO 3166-1 alpha-2)", example = "CI", required = true)
    private String countryCode;
    
    @Schema(description = "Adresse email (optionnel)", example = "user@example.com")
    private String email;
    
    @NotBlank
    @Schema(description = "Numéro de téléphone mobile", example = "002250777832982", required = true)
    private String mobilePhone;
    
    @Schema(description = "ID Facebook (si connexion via Facebook)")
    private String facebookId;
    
    @Schema(description = "ID Google (si connexion via Google)")
    private String googleId;

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
