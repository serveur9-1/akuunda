package org.akuunda.akuundawallet.keycloak.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@ToString
public class AuthenticationRequest {
    String username;
    String password;
    String grant_type;
    String client_id;
    String client_secret;
}
