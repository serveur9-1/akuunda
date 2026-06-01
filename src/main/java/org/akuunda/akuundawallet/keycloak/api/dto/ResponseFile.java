package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResponseFile {

    private String name;
    private String url;
    private String type;
    private String typePiece;
    private long size;
    private Users user;
}
