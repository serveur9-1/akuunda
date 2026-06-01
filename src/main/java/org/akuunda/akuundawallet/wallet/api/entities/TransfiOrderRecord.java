package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Local record of a TransFi order. Persisted right after POST /v3/orders succeeds when an
 * Akuunda {@code username} was attached to the request. Used to track and list operations
 * by Akuunda user without hitting the TransFi API.
 */
@Entity
@Table(name = "transfi_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransfiOrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true, length = 100)
    private String orderId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "transfi_user_id", length = 100)
    private String transfiUserId;

    @Column(name = "order_type", length = 50)
    private String orderType;

    @Column(length = 100)
    private String status;

    @Column(name = "source_currency", length = 20)
    private String sourceCurrency;

    @Column(name = "source_amount", precision = 20, scale = 8)
    private BigDecimal sourceAmount;

    @Column(name = "destination_currency", length = 20)
    private String destinationCurrency;

    @Column(name = "destination_amount", precision = 20, scale = 8)
    private BigDecimal destinationAmount;

    @Column(name = "fee_amount", precision = 20, scale = 8)
    private BigDecimal feeAmount;

    @Column(name = "fee_currency", length = 20)
    private String feeCurrency;

    @Column(name = "exchange_rate", length = 100)
    private String exchangeRate;

    @Column(name = "payment_url", columnDefinition = "text")
    private String paymentUrl;

    @Column(name = "purpose_code", length = 100)
    private String purposeCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
