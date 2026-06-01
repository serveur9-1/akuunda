package org.akuunda.akuundawallet.wallet.api.dto.partner;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PartnerContractResponse {
    private Long id;
    private String contractCode;
    private String partnerUsername;
    private String name;
    private String description;
    private String serviceType;
    private String triggerType;
    private String paymentLinkType;
    private Double cancellationPenaltyRate;
    private Integer freeCancellationHours;
    // TIME_BASED / DISPUTE_WINDOW
    private Integer autoReleaseHours;
    private Integer disputeWindowHours;
    // GEOLOCATION
    private Double expectedLatitude;
    private Double expectedLongitude;
    private Double geolocationRadiusMeters;
    private Boolean active;
    private List<BeneficiaryResponse> beneficiaries;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class BeneficiaryResponse {
        private Long id;
        private String beneficiaryType;
        private String label;
        private String walletAddress;
        private String username;
        private Double percentage;
        private Integer executionOrder;
    }
}
