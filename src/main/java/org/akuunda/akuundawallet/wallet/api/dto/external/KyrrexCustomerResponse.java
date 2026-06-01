package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Détails du client/membre Kyrrex")
public class KyrrexCustomerResponse {

    @Schema(description = "Adresse du client")
    private String address;

    @Schema(description = "Ville du client")
    private String city;

    @JsonProperty("country_id")
    @Schema(description = "ID du pays")
    private Integer countryId;

    @JsonProperty("country_of_incorporation_id")
    @Schema(description = "ID du pays d'incorporation")
    private Integer countryOfIncorporationId;

    @Schema(description = "Date de naissance")
    private String dob;

    @Schema(description = "Email du client")
    private String email;

    @JsonProperty("first_name")
    @Schema(description = "Prénom du client")
    private String firstName;

    @JsonProperty("last_name")
    @Schema(description = "Nom du client")
    private String lastName;

    @JsonProperty("level_info")
    @Schema(description = "Informations de niveau KYC")
    private Object levelInfo;

    @Schema(description = "Téléphone du client")
    private String phone;

    @JsonProperty("place_of_birth_id")
    @Schema(description = "ID du lieu de naissance")
    private Integer placeOfBirthId;

    @Schema(description = "Code postal")
    private String postcode;

    @JsonProperty("terms_accepted")
    @Schema(description = "Conditions acceptées")
    private Boolean termsAccepted;

    @JsonProperty("verification_status")
    @Schema(description = "Statut de vérification KYC")
    private String verificationStatus;
}
