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
@Schema(description = "Informations entité légale Kyrrex")
public class KyrrexLegalEntityInfoResponse {

    @JsonProperty("legal_name")
    @Schema(description = "Nom légal de l'entité")
    private String legalName;

    @Schema(description = "Code pays ISO 3166-1 alpha-2 (ex: FR)")
    private String country;

    @JsonProperty("registration_number")
    @Schema(description = "Numéro d'enregistrement légal de l'entité")
    private String registrationNumber;

    @Schema(description = "Statut KYB de l'entité légale (ex: pending, approved, rejected)")
    private String status;
}
