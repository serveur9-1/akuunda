package org.akuunda.akuundawallet.keycloak.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class BaseUserCommand implements Serializable {

    @NotBlank
    private String username;

    @NotBlank
    private String countryCode;

    private String firstName;

    private String lastName;

    private String email;

    @NotBlank
    private String mobilePhone;

    @NotBlank
    private String typeCompte; // PARTICULIER, ENTREPRISE

    private String adresse;

    private String siret;

    private String dateCreation;

    private String raisonSociale;
}
