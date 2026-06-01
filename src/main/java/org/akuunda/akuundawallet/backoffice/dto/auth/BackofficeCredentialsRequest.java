package org.akuunda.akuundawallet.backoffice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BackofficeCredentialsRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
