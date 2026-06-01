package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la réponse de migration des opérations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MigrationResponseDto {
    private boolean success;
    private String message;
    private MigrationStatistics statistics;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MigrationStatistics {
        private int totalOperations;
        private int fixedOperations;
        private int fixedGuardarianOperations;
        private int fixedDates;
        private int fixedStatuses;
        private int fixedAmounts;
        private int errors;
    }
}

