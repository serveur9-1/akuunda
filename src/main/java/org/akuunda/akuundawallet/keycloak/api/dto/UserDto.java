package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import java.sql.Timestamp;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {

    private String userId;
    String username;
    String mobilePhone;
    String password;
    String email;
    String genre;
    String firstname;
    String lastname;
    private Timestamp createdAt;
    private String dateNaissance;
    private String typeCompte;
    private boolean emailVerified;
    private boolean enabled;
    private boolean identityVerify;
    private String pinCode;
    private String siret;
    private String dateCreation;
    private String raisonSociale;
    private String adresse;
    private Timestamp cguAcceptationDate;
    private boolean cguAcceptation;

    /** KYC — pièce d'identité principale (NATIONAL_ID, PASSPORT, ...). */
    private String idType;
    private String idNumber;
    /** KYC — pièce d'identité secondaire (BVN/NIN au Nigéria). */
    private String additionalIdType;
    private String additionalIdNumber;

    private List<WalletUserDto> wallets;
}
