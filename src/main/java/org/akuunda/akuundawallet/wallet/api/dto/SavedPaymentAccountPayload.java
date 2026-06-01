package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload aligné sur le JSON Flutter {@code SavedPaymentAccount.toJson()} (camelCase).
 */
@Data
public class SavedPaymentAccountPayload {

    private String id;

    @NotBlank
    private String operatorName;

    @NotBlank
    private String operatorType;

    @NotBlank
    private String accountNumber;

    @NotBlank
    private String accountName;

    private String accountBank;

    @NotBlank
    private String countryCode;

    @NotBlank
    private String currencyCode;

    @NotBlank
    private String networkId;

    @NotBlank
    private String idType;

    @NotBlank
    private String idNumber;

    private String additionalIdType;

    private String additionalIdNumber;

    @NotBlank
    private String dateOfBirth;

    private String address;

    private String email;

    @JsonProperty("isDefault")
    @JsonAlias("defaultAccount")
    private boolean defaultAccount;
}
