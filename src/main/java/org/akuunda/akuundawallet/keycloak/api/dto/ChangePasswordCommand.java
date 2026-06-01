package org.akuunda.akuundawallet.keycloak.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import java.io.Serial;
import java.io.Serializable;

@Data
@ToString
public class ChangePasswordCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 5945124404545966197L;

    @NotBlank
    private String userName;

    @NotBlank
    private String otp;

    @NotBlank
    private String newPassword;

    @NotBlank
    private String confirmPassword;

}
