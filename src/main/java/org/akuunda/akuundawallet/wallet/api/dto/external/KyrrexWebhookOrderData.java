package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Données webhook ordre/swap Kyrrex")
public class KyrrexWebhookOrderData {

    private String amount;
    private String createdAt;
    private String currency;
    private String executed;
    private String inputCurrency;
    private String outputAmount;
    private String outputCurrency;
    private String state;
    private String uid;
}
