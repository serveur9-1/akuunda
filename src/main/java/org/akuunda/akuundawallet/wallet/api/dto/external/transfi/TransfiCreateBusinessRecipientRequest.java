package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de POST /v3/recipients/business.
 * Champs obligatoires côté TransFi : countryOfIncorporation, country, currencyType, businessName.
 * accountIdentifier peut être requis selon le couple (geo, currencyType, type=organization)
 * exposé par POST /v3/recipients/mandatory-fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiCreateBusinessRecipientRequest {
    private String countryOfIncorporation;
    private String country;
    private String currencyType;
    private String businessName;
    private TransfiAccountIdentifierDto accountIdentifier;
}
