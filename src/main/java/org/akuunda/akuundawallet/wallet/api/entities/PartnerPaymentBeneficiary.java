package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "partner_payment_beneficiaries",
       indexes = @Index(name = "idx_ppb_payment", columnList = "payment_id"))
@Builder @Data @NoArgsConstructor @AllArgsConstructor
public class PartnerPaymentBeneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private PartnerContractPayment payment;

    @Column(nullable = false, length = 50)
    private String beneficiaryType;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(length = 255)
    private String walletAddress;

    @Column(length = 255)
    private String username;

    @Column(nullable = false)
    private Double percentage;

    @Column(nullable = false)
    @Builder.Default
    private Integer executionOrder = 1;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
