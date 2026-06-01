package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Destination d'un ordre TransFi (champ POST /v3/orders.destination).
 *
 * currency obligatoire (fiat ou crypto).
 * amount : montant cible côté destination.
 * paymentCode / paymentType : pertinents si currency est fiat (cf. /v3/config/payment-methods).
 * walletAddress : pertinent si currency est crypto.
 * qrCode : optionnel, uniquement pour les flux QR Payouts.
 * paymentType : bank_transfer | card | local_wallet
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiOrderDestinationDto {
    private String currency;
    private Double amount;
    private String paymentCode;
    private String paymentType;
    private String walletAddress;
    private String qrCode;
    private Map<String, Object> additionalPaymentDetails;
}
