package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class LoginRequest {
    String username;
    String otpCode;
}
