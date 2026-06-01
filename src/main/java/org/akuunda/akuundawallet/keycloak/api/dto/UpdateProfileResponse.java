package org.akuunda.akuundawallet.keycloak.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProfileResponse {
    private String status;
    private String message;
    private UserProfileData data;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserProfileData {
        private String userId;
        private String username;
        private String firstName;
        private String lastName;
        private String email;
        private String address;
        private String dateNaissance;
        private String siret;
        private String raisonSociale;
        private String accountType;
        private String idType;
        private String idNumber;
        private String additionalIdType;
        private String additionalIdNumber;
    }
}
