package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête de dépôt fiat via carte Kyrrex")
public class KyrrexFiatDepositRequest {

    @JsonProperty("deposit_address_uid")
    @Schema(description = "UID de l'adresse de dépôt")
    private String depositAddressUid;

    @JsonProperty("provider_id")
    @Schema(description = "Identifiant du provider")
    private String providerId;

    @Schema(description = "Montant du dépôt")
    private BigDecimal amount;

    @JsonProperty("redirect_url")
    @Schema(description = "URL de redirection après paiement")
    private String redirectUrl;
}
