package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class ManageCreditRequestDto {

    private String msisdn;
    private String mvnoRef;
    private String source;
    private String transactionReference;
    private CreditDto credit;

    @Data
    public static class CreditDto {
        /** "add", "remove" or "set" */
        private String mode;
        private Long amount;
        /** "CENTS" by default */
        private String unit;
        private String expirationDate;
    }
}
