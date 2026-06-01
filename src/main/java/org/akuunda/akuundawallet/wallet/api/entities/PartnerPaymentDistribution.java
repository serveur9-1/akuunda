package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Enregistrement d'un transfert de redistribution exécuté
 * lors de la libération d'un paiement partenaire.
 */
@Entity
@Table(name = "partner_payment_distributions")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartnerPaymentDistribution implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private PartnerContractPayment payment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "beneficiary_id", nullable = true)
    private PartnerContractBeneficiary beneficiary;

    @Column(nullable = false, length = 255)
    private String walletAddress;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private Double percentage;

    @Column(length = 255)
    private String txHash;

    /** pending | success | failed */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "pending";

    @Column
    private LocalDateTime executedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
