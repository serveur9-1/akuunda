package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleDto implements Serializable {
    @Serial
    private static final long serialVersionUID = -1L;

    private String id;
    private String name;
    private String fullPath;
    private String comment;
    private String idParent;
    private String nameParent;
    private String createdFrom;

}
