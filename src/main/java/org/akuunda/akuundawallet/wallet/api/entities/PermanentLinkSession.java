package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "permanent_link_sessions", indexes = {
    @Index(name = "idx_permanent_link_session_code", columnList = "session_code", unique = true)
})
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermanentLinkSession implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 12)
    private String sessionCode;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "permanent_link_id", nullable = false)
    private PermanentLink permanentLink;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = true, length = 10)
    private String currency;

    @Column(nullable = true, length = 200)
    private String reference;

    @Column(nullable = false, length = 20)
    private String status; // CREATED, PENDING, PAID, SWEPT, EXPIRED, FAILED, CANCELLED

    @Column(name = "payer_name", length = 200)
    private String payerName;

    @Column(name = "payer_phone", length = 50)
    private String payerPhone;

    @Column(name = "payer_email", length = 200)
    private String payerEmail;

    @Column(name = "payment_id_bytes32", length = 66)
    private String paymentIdBytes32;

    @Column(name = "create2_wallet_address", length = 42)
    private String create2WalletAddress;

    @Column(name = "create_tx_hash", length = 66)
    private String createTxHash;

    @Column(name = "execute_tx_hash", length = 66)
    private String executeTxHash;

    @Column(name = "external_transaction_id", length = 100)
    private String externalTransactionId;

    @Column(name = "payment_provider", length = 20)
    private String paymentProvider;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "provider_widget_url", length = 2000)
    private String providerWidgetUrl;

    @Column(name = "partner_contract_payment_code", length = 50)
    private String partnerContractPaymentCode;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
