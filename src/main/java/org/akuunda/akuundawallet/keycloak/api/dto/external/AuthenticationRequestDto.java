package org.akuunda.akuundawallet.keycloak.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequestDto {

    private String grant_type;
    private String client_id;
    private String client_secret;

}
