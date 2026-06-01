package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OtpResponse {

    private String id;
    private String otpCode;
    private Timestamp dateCreate;
    private Timestamp dateValidated;
    private String status;
}
