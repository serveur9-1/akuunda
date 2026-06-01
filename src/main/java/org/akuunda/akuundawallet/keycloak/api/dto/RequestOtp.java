package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Data;

@Data
public class RequestOtp {

    String username;
    String type; ///SMS or WHATSAPP
    String action;  //changePassword or updateUser
}
