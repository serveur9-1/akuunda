package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Source d'un ordre TransFi (champ POST /v3/orders.source).
 *
 * currency obligatoire (fiat ou crypto, ex: USD, EUR, USDT, USDTPOLYGON).
 * paymentCode / paymentType : pertinents si currency est fiat (cf. /v3/config/payment-methods).
 * sendersWalletAddress / walletAddress : pertinents si currency est crypto.
 * paymentType : bank_transfer | card | local_wallet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiOrderSourceDto {
    private String currency;
    private Double amount;
    private String paymentCode;
    private String paymentType;
    private String sendersWalletAddress;
    private String walletAddress;
    private Map<String, Object> additionalPaymentDetails;
}
