package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Paiement conditionnel effectué sous un contrat partenaire.
 * Cycle de vie : pending_condition → distributed | refunded | refunded_partial | failed
 */
@Entity
@Table(name = "partner_contract_payments", indexes = {
    @Index(name = "idx_pcp_contract", columnList = "contract_id"),
    @Index(name = "idx_pcp_status",   columnList = "status"),
    @Index(name = "idx_pcp_client",   columnList = "client_username"),
    @Index(name = "idx_pcp_qr",       columnList = "qr_token")
})
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartnerContractPayment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String paymentCode;

    /** keccak256(paymentCode) — ID utilisé on-chain dans deposit() / distribute() */
    @Column(length = 66)
    private String onChainPaymentId;

    /** Adresse CREATE2 générée par AkuundaPaymentFactory — à communiquer au client pour le virement */
    @Column(length = 66)
    private String create2WalletAddress;

    /** Statut de la réception sur le CREATE2 (waiting_payment → payment_received → deposited) */
    @Column(length = 30)
    @Builder.Default
    private String create2Status = "waiting_payment";

    @JsonIgnore
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "contract_id", nullable = false)
    private PartnerContract contract;

    @Column(nullable = false)
    private String clientUsername;

    /** Contact direct si le client n'a pas de compte Akuunda */
    @Column(length = 30)
    private String clientPhone;

    @Column(length = 255)
    private String clientEmail;

    @Column(length = 255)
    private String clientWalletId;

    @Column(length = 255)
    private String escrowWalletId;

    @Column(length = 255)
    private String intermediateWalletId;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "USDC";

    @Column
    private Double localAmount;

    @Column(length = 10)
    private String localCurrency;

    /**
     * pending_condition  : Fonds en séquestre, en attente de validation
     * condition_validated: Validation reçue, redistribution en cours
     * distributed        : Fonds redistribués avec succès
     * refunded           : Remboursement total
     * refunded_partial   : Remboursement partiel (avec pénalités)
     * failed             : Échec de redistribution (intervention manuelle requise)
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "pending_condition";

    @Column(length = 255)
    private String depositTxHash;

    /** JSON array des hashes de redistribution (un par bénéficiaire) */
    @Column(columnDefinition = "TEXT")
    private String distributionTxHashes;

    @Column(length = 255)
    private String refundTxHash;

    // ── QR Code ──
    @Column(length = 255, unique = true)
    private String qrToken;

    @Column(length = 500)
    private String qrUrl;

    @Column
    private LocalDateTime qrExpiresAt;

    @Column
    private LocalDateTime qrScannedAt;

    @Column(length = 255)
    private String qrScannedBy;

    // ── Webhook ──
    @Column(length = 500)
    private String webhookUrl;

    @Column(length = 255)
    private String webhookSecret;

    @Column
    private LocalDateTime webhookValidatedAt;

    // ── REMOTE_CONFIRMATION ──
    @Column(length = 255, unique = true)
    private String confirmationToken;

    @Column(length = 500)
    private String confirmationUrl;

    @Column
    private LocalDateTime confirmationTokenExpiresAt;

    @Column
    private LocalDateTime confirmedByClientAt;

    // ── OTP ──
    @Column(length = 10)
    private String otpCode;

    @Column
    private LocalDateTime otpExpiresAt;

    @Column
    private LocalDateTime otpUsedAt;

    @Column(length = 255)
    private String otpUsedBy;

    // ── DUAL_CONFIRMATION ──
    @Column
    private LocalDateTime providerConfirmedAt;

    @Column(length = 255)
    private String providerConfirmedBy;

    @Column
    private LocalDateTime clientDualConfirmedAt;

    // ── TIME_BASED / DISPUTE_WINDOW ──
    @Column
    private LocalDateTime autoReleaseScheduledAt;

    @Column
    private LocalDateTime disputedAt;

    @Column(length = 255)
    private String disputedBy;

    @Column(columnDefinition = "TEXT")
    private String disputeReason;

    // ── GEOLOCATION ──
    @Column
    private Double clientLatitude;

    @Column
    private Double clientLongitude;

    @Column
    private LocalDateTime locationConfirmedAt;

    // ── ADMIN_APPROVAL ──
    @Column
    private LocalDateTime adminApprovalRequestedAt;

    @Column
    private LocalDateTime adminApprovedAt;

    @Column(length = 255)
    private String adminApprovedBy;

    // ── Dates métier ──
    @Column
    private LocalDateTime serviceStartDate;

    @Column
    private LocalDateTime cancellationDeadline;

    @Column
    private LocalDateTime distributedAt;

    @Column(length = 500)
    private String cancellationReason;

    @Column
    private Double refundedAmount;

    @Column
    private Double retainedAmount;

    // LAZY : évite N SELECT par entité lors du chargement de listes de paiements.
    // Tous les accès à ces collections sont dans des méthodes @Transactional (lazy loading OK).
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PartnerPaymentDistribution> distributions = new ArrayList<>();

    // ── DYNAMIC_ASSIGNMENT : bénéficiaires assignés par l'admin après paiement ──
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("executionOrder ASC")
    @Builder.Default
    private List<PartnerPaymentBeneficiary> dynamicBeneficiaries = new ArrayList<>();

    @Column
    private LocalDateTime beneficiariesAssignedAt;

    @Column(length = 255)
    private String assignedBy;

    /** configId on-chain enregistré via registerConfig() lors de l'assignation */
    @Column(length = 100)
    private String dynamicOnChainConfigId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
