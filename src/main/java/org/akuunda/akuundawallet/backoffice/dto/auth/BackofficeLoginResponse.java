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
public class BackofficeLoginResponse {
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
    private String tokenType;
    private BackofficeUserInfo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackofficeUserInfo {
        private String id;
        private String name;
        private String email;
        private String role;
        private List<String> permissions;
        private String portal;
        private Boolean twoFactorRequired;
    }
}
