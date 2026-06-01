package org.akuunda.akuundawallet.keycloak.api.dto.external.sumsub;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccessToken {

    private String userId;
    private String token;

}
