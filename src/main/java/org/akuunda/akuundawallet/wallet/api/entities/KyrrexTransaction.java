package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "kyrrex_transactions", indexes = {
        @Index(name = "idx_kyrrex_tx_username", columnList = "username"),
        @Index(name = "idx_kyrrex_tx_kyrrex_id", columnList = "kyrrex_id", unique = true),
        @Index(name = "idx_kyrrex_tx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KyrrexTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "kyrrex_id", unique = true)
    private String kyrrexId;

    @Column(name = "sequence_id")
    private String sequenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private KyrrexTransactionType type;

    /** Statut stocké en String pour accepter les statuts Kyrrex bruts */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "asset", length = 20)
    private String asset;

    @Column(name = "amount", precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "fee", precision = 20, scale = 8)
    private BigDecimal fee;

    @Column(name = "rate", precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "fiat_amount", precision = 20, scale = 8)
    private BigDecimal fiatAmount;

    @Column(name = "fiat_currency", length = 10)
    private String fiatCurrency;

    @Column(name = "crypto_amount", precision = 20, scale = 8)
    private BigDecimal cryptoAmount;

    @Column(name = "crypto_asset", length = 20)
    private String cryptoAsset;

    @Column(name = "redirect_url", length = 1024)
    private String redirectUrl;

    @Column(name = "provider_id", length = 100)
    private String providerId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "instrument", length = 50)
    private String instrument;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Horodatage du règlement effectif vers le wallet Akuunda Pay
     * (crédit ledger). Reste {@code null} tant que le webhook n'a pas confirmé
     * un statut terminal de succès ou que le service de réconciliation n'a pas
     * encore traité la transaction. Sert de garde-fou anti double-crédit.
     */
    @Column(name = "settled_at")
    private Instant settledAt;

    /** Montant crédité au wallet Akuunda Pay dans l'asset cible (USDC par défaut). */
    @Column(name = "settlement_amount", precision = 20, scale = 8)
    private BigDecimal settlementAmount;

    /** Asset cible du règlement (USDC). */
    @Column(name = "settlement_asset", length = 20)
    private String settlementAsset;

    /** Réseau cible pour le sweep on-chain back-office (Polygon). */
    @Column(name = "settlement_network", length = 50)
    private String settlementNetwork;

    /** UID du retrait Kyrrex initié pour le payout on-chain vers le wallet utilisateur. */
    @Column(name = "payout_withdrawal_id", length = 255)
    private String payoutWithdrawalId;

    /** Statut technique du payout on-chain (PENDING, SUCCESS, FAILED, SKIPPED...). */
    @Column(name = "payout_status", length = 40)
    private String payoutStatus;

    /** Horodatage de la première tentative de payout on-chain. */
    @Column(name = "payout_started_at")
    private Instant payoutStartedAt;

    /** Horodatage de confirmation finale du payout on-chain. */
    @Column(name = "payout_confirmed_at")
    private Instant payoutConfirmedAt;

    @JsonIgnore
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
