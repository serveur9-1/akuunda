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
@Schema(description = "Kyrrex fiat withdrawal bank details request")
public class KyrrexFiatWithdrawalBankDetailsRequest {

    private BigDecimal amount;

    private String currency;

    private String iban;

    private String bic;

    @JsonProperty("beneficiary_name")
    private String beneficiaryName;

    @JsonProperty("provider_id")
    private String providerId;
}
