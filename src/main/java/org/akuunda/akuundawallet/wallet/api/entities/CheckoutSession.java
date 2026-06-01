package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "checkout_sessions",
    indexes = {
        @Index(name = "idx_checkout_code", columnList = "checkout_code", unique = true),
        @Index(name = "idx_checkout_idempotency", columnList = "api_key_id,idempotency_key")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_checkout_idempotency", columnNames = {"api_key_id", "idempotency_key"})
    }
)
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checkout_code", nullable = false, unique = true, length = 50)
    private String checkoutCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "api_key_id", nullable = false)
    private MerchantApiKey merchantApiKey;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "permanent_link_id", nullable = false)
    private PermanentLink permanentLink;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "merchant_reference", nullable = false, length = 200)
    private String merchantReference;

    @Column(length = 500)
    private String description;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "cancel_url", length = 500)
    private String cancelUrl;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false, length = 20)
    private String status;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_session_id")
    private PermanentLinkSession paymentSession;

    @Column(name = "webhook_sent", nullable = false)
    private Boolean webhookSent;

    @Column(name = "webhook_sent_at")
    private LocalDateTime webhookSentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Header Idempotency-Key associé à la requête de création. */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    /** Mode opératoire : "live" ou "test". */
    @Column(name = "mode", length = 10)
    private String mode;

    /**
     * Pays cible défini par le marchand (code ISO 3166-1 alpha-2, ex. "CI", "SN").
     * Si renseigné, la page de paiement sélectionne ce pays automatiquement —
     * aucune interaction demandée à l'acheteur.
     */
    @Column(name = "payment_country", length = 5)
    private String paymentCountry;

    /** Montant total remboursé (0 ou null si aucun remboursement). */
    @Column(name = "refunded_amount")
    private Double refundedAmount;

    /** Date du dernier remboursement. */
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** Raison libre du remboursement. */
    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "CREATED";
        if (webhookSent == null) webhookSent = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
