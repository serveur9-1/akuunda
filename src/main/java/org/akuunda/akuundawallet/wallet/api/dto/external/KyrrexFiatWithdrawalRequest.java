package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Requête de retrait fiat Kyrrex")
public class KyrrexFiatWithdrawalRequest {

    @JsonProperty("payout_method")
    @Schema(description = "Méthode de payout")
    private String payoutMethod;

    @Schema(description = "Instrument")
    private String instrument;

    @JsonProperty("provider_id")
    @Schema(description = "Identifiant du provider")
    private String providerId;

    @Schema(description = "Montant")
    private BigDecimal amount;

    @Schema(description = "Devise")
    private String currency;

    @Schema(description = "IBAN")
    private String iban;

    @JsonProperty("first_name")
    @Schema(description = "Prénom")
    private String firstName;

    @JsonProperty("last_name")
    @Schema(description = "Nom")
    private String lastName;

    @JsonProperty("country_id")
    @Schema(description = "Identifiant du pays")
    private Integer countryId;

    @Schema(description = "Adresse")
    private String address;

    @Schema(description = "Ville")
    private String city;

    @Schema(description = "Code postal")
    private String postcode;

    @JsonProperty("withdrawal_address_uid")
    @Schema(description = "UID de l'adresse de retrait")
    private Long withdrawalAddressUid;
}
