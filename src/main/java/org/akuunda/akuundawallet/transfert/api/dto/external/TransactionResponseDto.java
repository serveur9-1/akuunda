package org.akuunda.akuundawallet.transfert.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for the response of a transaction request.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponseDto {

    private boolean success;
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Result {
        private String id;
        private String transactionHash;
        /** "SUCCEEDED", "FAILED", "UNKNOWN" — retourné par Venly après minage */
        private String status;
        private Object transactionDetails;

        public boolean isOnChainSuccess() {
            // Si le champ n'est pas renvoyé (null), on ne bloque pas — txHash présent suffit
            if (status == null) return transactionHash != null;
            return "SUCCEEDED".equalsIgnoreCase(status);
        }
    }
}
