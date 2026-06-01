package org.akuunda.akuundawallet.backoffice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BackofficeLoginRequest {
    @NotBlank
    private String email;
    @NotBlank
    private String password;
    @NotBlank
    private String portal; // "admin" | "pro"
}
