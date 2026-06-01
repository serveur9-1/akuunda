package org.akuunda.akuundawallet.keycloak.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

@Getter
@ToString
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Result {

    public String id;
    public String reference;
    public String createdAt;
    List<ExternalSigningMethod> signingMethods;
}
