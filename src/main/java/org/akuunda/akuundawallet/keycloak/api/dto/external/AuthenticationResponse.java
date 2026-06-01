package org.akuunda.akuundawallet.keycloak.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {

        public String access_token;
        public int expires_in;
        public int refresh_expires_in;
        public String token_type;
        public int not_before_policy;
        public String scope;

}
