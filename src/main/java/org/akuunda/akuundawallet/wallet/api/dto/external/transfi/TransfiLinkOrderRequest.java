package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body de POST /v3/orders/link.
 *
 * Lie un dépôt IBAN "unmapped" (sans utilisateur identifié) à un utilisateur
 * enregistré. Après les contrôles de compliance :
 *  - succès → ordre marqué fund_settled
 *  - échec  → ordre reste en initiated
 *
 * Les deux champs sont requis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiLinkOrderRequest {
    /**
     * Akuunda wallet username. If {@code userId} is blank, resolved from {@code transfi_users}.
     * Never forwarded to TransFi (WRITE_ONLY).
     */
    @Schema(description = "Username wallet Akuunda — résout userId via transfi_users si userId vide",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;

    private String orderId;
    private String userId;
}
