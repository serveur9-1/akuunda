package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Détails d'une transaction Meld")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // <-- ignore les champs inconnus
public class MeldTransactionDetails {

        @JsonProperty("transactionId")
        private String transactionId;

        @JsonProperty("externalCustomerId")
        private String externalCustomerId;

        @JsonProperty("status")
        private String status; // Exemple: PENDING, SETTLING, SETTLED, FAILED, CANCELLED

        @JsonProperty("sourceCurrencyCode")
        private String sourceCurrencyCode;

        @JsonProperty("destinationCurrencyCode")
        private String destinationCurrencyCode;

        @JsonProperty("sourceAmount")
        private BigDecimal sourceAmount;

        @JsonProperty("destinationAmount")
        private BigDecimal destinationAmount;

        @JsonProperty("fees")
        private BigDecimal fees;

        @JsonProperty("feesCurrency")
        private String feesCurrency;

        @JsonProperty("walletAddress")
        private String walletAddress;

        @JsonProperty("paymentMethod")
        private String paymentMethod; // ex: CARD, BANK_TRANSFER, CRYPTO

        @JsonProperty("createdAt")
        private Instant createdAt;

        @JsonProperty("updatedAt")
        private Instant updatedAt;

        @JsonProperty("transactionReference")
        private String transactionReference; // référence interne ou externe

        @JsonProperty("additionalInfo")
        private String additionalInfo; // info optionnelle
}
