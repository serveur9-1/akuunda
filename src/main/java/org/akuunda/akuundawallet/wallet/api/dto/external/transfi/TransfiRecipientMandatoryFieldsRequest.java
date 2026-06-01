package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de POST /v3/recipients/mandatory-fields.
 * geo            : code pays ISO 2 lettres (ex: "ID")
 * currencyType   : fiat | crypto
 * type           : individual | organization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiRecipientMandatoryFieldsRequest {
    private String geo;
    private String currencyType;
    private String type;
}
