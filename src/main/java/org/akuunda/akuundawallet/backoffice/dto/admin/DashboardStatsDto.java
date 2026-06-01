package org.akuunda.akuundawallet.backoffice.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardStatsDto {
    private Long totalUsers;
    private Long activeUsers;
    private Long newUsersToday;
    private Long totalTransactions;
    private Map<String, Double> transactionVolume;
    /** Volume total traité converti en USD (on/off-ramp en devise locale + flux USDC). */
    private Double totalVolumeUsd;
    private Long pendingKYC;
    private Long activeESIMs;
    private Long activePaymentLinks;
    private Map<String, Double> escrowBalance;
    private Map<String, Double> revenueToday;
    private Map<String, Double> revenueThisMonth;
    private Double failureRate;
    private Double averageProcessingTime;
}
