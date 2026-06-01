package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class LoginSocialRequest {
    String username;
    String typeLogin; // "google", or "facebook"
}
