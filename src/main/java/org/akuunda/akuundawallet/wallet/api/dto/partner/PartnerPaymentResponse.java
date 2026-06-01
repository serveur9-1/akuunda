package org.akuunda.akuundawallet.wallet.api.dto.partner;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PartnerPaymentResponse {
    private String paymentCode;
    private String contractCode;
    private String contractName;
    private String clientUsername;
    private Double amount;
    private String currency;
    private String status;

    /** Adresse CREATE2 où le client doit envoyer ses USDC */
    private String paymentAddress;
    private String create2Status;

    // QR Code (si applicable)
    private String qrToken;
    private String qrUrl;
    private LocalDateTime qrExpiresAt;

    // Webhook (si applicable)
    private String webhookUrl;

    private LocalDateTime serviceStartDate;
    private LocalDateTime cancellationDeadline;
    private LocalDateTime distributedAt;
    private LocalDateTime createdAt;

    /** Informations spécifiques au mode de libération configuré */
    private LiberationInfo liberationInfo;

    private List<DistributionDetail> distributions;

    @Data
    @Builder
    public static class LiberationInfo {
        private String mode;
        // REMOTE_CONFIRMATION
        private String  confirmationUrl;
        private LocalDateTime confirmationExpiry;
        private Boolean clientConfirmed;
        // OTP
        private LocalDateTime otpExpiry;
        private Boolean otpUsed;
        // DUAL_CONFIRMATION
        private Boolean providerConfirmed;
        private Boolean clientDualConfirmed;
        // TIME_BASED / DISPUTE_WINDOW
        private LocalDateTime autoReleaseScheduledAt;
        private Boolean disputed;
        // GEOLOCATION
        private Boolean locationConfirmed;
        // ADMIN_APPROVAL
        private LocalDateTime adminApprovalRequestedAt;
        private Boolean adminApproved;
    }

    @Data
    @Builder
    public static class DistributionDetail {
        private String beneficiaryLabel;
        private String beneficiaryType;
        private String walletAddress;
        private Double amount;
        private Double percentage;
        private String txHash;
        private String status;
        private LocalDateTime executedAt;
    }
}
