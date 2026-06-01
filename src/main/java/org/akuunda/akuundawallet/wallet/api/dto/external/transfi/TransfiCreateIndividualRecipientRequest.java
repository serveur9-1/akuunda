package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de POST /v3/recipients/individual.
 * Champs obligatoires côté TransFi : country, currencyType.
 * Les autres champs sont mandataires ou non selon la réponse de
 * POST /v3/recipients/mandatory-fields pour le couple (geo, currencyType, type=individual).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiCreateIndividualRecipientRequest {
    private String country;
    private String currencyType;
    private String firstName;
    private String lastName;
    private TransfiAccountIdentifierDto accountIdentifier;
}
