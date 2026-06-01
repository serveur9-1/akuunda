package org.akuunda.akuundawallet.keycloak.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@ToString
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalSigningMethod {

    String id;
    String type;
    int incorrectAttempts;
    int remainingAttempts;
    boolean hasMasterSecret;

}
