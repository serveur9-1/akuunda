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
@Schema(description = "Requête de retrait crypto Kyrrex")
public class KyrrexCryptoWithdrawalRequest {

    @JsonProperty("dchain")
    @Schema(description = "Identifiant de chaîne (dchain)", example = "btc")
    private String currency;

    @Schema(description = "Montant")
    private BigDecimal amount;

    @JsonProperty("requisite_uid")
    @Schema(description = "UID du réquisite")
    private String requisiteId;
}
