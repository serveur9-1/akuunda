package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Body de POST /v3/iban/create-iban.
 *
 * Requis : currency, paymentCode.
 *
 * customer (optionnel) :
 *  - si customer.userId est fourni → IBAN créé pour cet utilisateur
 *  - si customer omis (ou userId absent) → IBAN créé pour l'organisation
 *  - les champs additionnels (email, street, city...) dépendent de
 *    requiredFields retourné par GET /v3/config/payment-methods/iban
 *
 * uiEnabled : si true, la réponse contient un redirectUrl pour finaliser
 * la création via une UI plutôt que créer l'IBAN directement.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiCreateIbanRequest {
    /**
     * Akuunda wallet username (phone). If present, the IBAN is persisted to {@code transfi_ibans}
     * for later listing via GET /transfi/iban?username=... Never sent to TransFi (WRITE_ONLY).
     */
    @Schema(description = "Username wallet Akuunda — persiste l'IBAN dans transfi_ibans (non transmis à TransFi)",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;

    private String currency;
    private String paymentCode;
    private Boolean uiEnabled;
    private Map<String, Object> customer;
}
