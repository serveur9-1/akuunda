package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "meld_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeldTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id; // UUID généré par la DB

    @Column(nullable = false, unique = true)
    private String transactionId; // ID fourni par Meld

    @Column(nullable = false)
    private String externalCustomerId; // username ou id externe

    @Column(nullable = false)
    private String status;

    private String sourceCurrencyCode;
    private String destinationCurrencyCode;
    private BigDecimal sourceAmount;
    private BigDecimal destinationAmount;
    private BigDecimal fees;
    private String feesCurrency;
    private String walletAddress;
    private String paymentMethod;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
