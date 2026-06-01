package org.akuunda.akuundawallet.keycloak.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
public class AddUserCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 5945124404545966197L;

    @NotBlank
    private String username;

    private String lastName;

    private String dateNaissance;  // remove this field

    private String firstName;

    @NotBlank
    private String countryCode;

    private String email;

    @NotBlank
    private String mobilePhone;

    @NotBlank
    private boolean cguAcceptation;

    @NotBlank
    private String typeCompte;  // PARTICULIER , ENTREPRISE
    @NotBlank
    private String pinCode; // remove this field generate by me

    private String siret;
    private String dateCreation;
    private String raisonSociale;
    private String adresse;

    private String facebookId;
    private String googleId;
}
