package org.akuunda.akuundawallet.keycloak.api.dto.external.infobip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Destination {

    @JsonProperty("to")
    private String myto;
}
