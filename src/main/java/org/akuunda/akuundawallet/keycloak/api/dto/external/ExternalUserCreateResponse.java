package org.akuunda.akuundawallet.keycloak.api.dto.external;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@ToString
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalUserCreateResponse {

        private boolean success;
        private Result result;

}
