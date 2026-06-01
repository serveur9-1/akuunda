package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@Setter
public class CreateUserRequest {

    private String userName;
    private String userGenre;
    private String password;
    private String dateNaissance;
    private String email;
    private String firstName;
    private String lastName;
    private String countryCode;
    private String phoneNumber;
    private String accountType;
    private String siret;
    private String dateCreation;
    private String raisonSociale;
    private String adresse;
    private String codePin;
}
