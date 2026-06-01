package org.akuunda.akuundawallet.backoffice.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileDto {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String avatar;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String lastLoginAt;

    // Compte
    private String typeCompte;
    private Boolean kycVerified;
    private Boolean cguAcceptation;

    // Localisation (depuis le wallet)
    private String countryCode;
    private String countryName;
    private String currencyCode;

    // Wallets
    private List<WalletUserDto> wallets;

    // Champs backoffice (à brancher plus tard)
    private String kycLevel;
    private String kycStatus;
    private Boolean twoFactorEnabled;
    private String preferredLanguage;
    private String referralCode;
    private Map<String, Object> metadata;
}
