package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "one_time_payment_links", indexes = {
    @Index(name = "idx_one_time_payment_link_unique_code", columnList = "unique_code", unique = true)
})
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OneTimePaymentLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unique_code", nullable = false, unique = true, length = 60)
    private String uniqueCode;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = true)
    private Users creator;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = true)
    private Double amount;

    @Column(nullable = true, length = 10)
    private String currency;

    @Column(name = "source_amount", nullable = true)
    private Double sourceAmount;

    @Column(name = "source_currency", nullable = true, length = 10)
    private String sourceCurrency;

    @Column(nullable = false, length = 50)
    private String status; // CREATED, PENDING, PAID, EXPIRED, CANCELLED, FAILED

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "payer_phone", length = 50)
    private String payerPhone;

    @Column(name = "payer_name", length = 200)
    private String payerName;

    @Column(name = "payer_email", length = 200)
    private String payerEmail;

    @Column(name = "yellow_card_transaction_id", length = 200)
    private String yellowCardTransactionId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_id_bytes32", length = 66)
    private String paymentIdBytes32;

    @Column(name = "create2_wallet_address", length = 42)
    private String create2WalletAddress;

    @Column(name = "create_tx_hash", length = 66)
    private String createTxHash;

    @Column(name = "execute_tx_hash", length = 66)
    private String executeTxHash;

    @Column(name = "deposit_tx_hash", length = 66)
    private String depositTxHash;

    @Column(name = "link_type", nullable = false, length = 20)
    private String linkType; // "SIMPLE" (default) ou "CONDITIONAL" (escrow)

    @Column(name = "service_type", length = 50)
    private String serviceType; // HOTEL, TRAVEL_AGENCY, TOURISM, RENTAL, DELIVERY

    @Column(name = "service_start_date")
    private LocalDateTime serviceStartDate;

    @Column(name = "cancellation_deadline")
    private LocalDateTime cancellationDeadline;

    @Column(name = "conditional_payment_id")
    private Long conditionalPaymentId; // Référence vers ConditionalPayment créé après paiement

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @Column(name = "qr_code_token", length = 100)
    private String qrCodeToken;

    @Column(name = "provider_widget_url", length = 2000)
    private String providerWidgetUrl;

    // YellowCard 2-step flow — stored at link creation, used during confirm-pay-yc
    @Column(name = "yc_channel_id")
    private String ycChannelId;

    @Column(name = "yc_country", length = 10)
    private String ycCountry;

    @Column(name = "yc_currency", length = 10)
    private String ycCurrency;

    @Column(name = "yc_reason", length = 200)
    private String ycReason;

    @Column(name = "yc_recipient_json", columnDefinition = "TEXT")
    private String ycRecipientJson;

    @Column(name = "yc_source_json", columnDefinition = "TEXT")
    private String ycSourceJson;

    // akuundapay_generelinkYC integration — populated during createConditionalLinkYc
    @Column(name = "yc_sequence_id", length = 100)
    private String ycSequenceId;

    @Column(name = "yc_payment_url", length = 2000)
    private String ycPaymentUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "CREATED";
        }
        if (linkType == null) {
            linkType = "SIMPLE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
