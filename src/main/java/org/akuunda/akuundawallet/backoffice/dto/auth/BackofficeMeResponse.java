package org.akuunda.akuundawallet.backoffice.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackofficeMeResponse {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String avatar;
    private String role;
    private List<String> permissions;
    private String portal;
    private Boolean twoFactorEnabled;
    private String lastLoginAt;
    private String sessionExpiresAt;
}
