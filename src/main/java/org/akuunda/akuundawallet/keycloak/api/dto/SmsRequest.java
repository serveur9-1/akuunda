package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class SmsRequest {
    String message;
    String type; ///SMS or WHATSAPP
    String destinataire;

}
