package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SigninRequest {

    String methodValue;
    String methodType;
    String userName;
    String signingPin;
    String physicalDeviceId;
}
