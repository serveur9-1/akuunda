package org.akuunda.akuundawallet.backoffice.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BackofficeRegisterRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    private String firstName;

    private String lastName;

    /**
     * Portail cible : rôle realm Keycloak configuré via {@code backoffice.keycloak.realm-role.*}.
     */
    @NotBlank
    private String portal;
}
