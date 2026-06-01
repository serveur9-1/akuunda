package org.akuunda.akuundawallet.backoffice.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConditionalPaymentDto {
    private String id;
    private String reference;
    private String status;
    private Double amount;
    private String currency;
    private Object sender;
    private Object receiver;
    private List<ConditionDto> conditions;
    private String escrowWalletId;
    private String releaseSchedule;
    private String disputeReason;
    private String expiresAt;
    private String createdAt;
    private String updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionDto {
        private String id;
        private String description;
        private String type;
        private String status;
        private String dueDate;
        private String metAt;
        private String evidence;
    }
}
