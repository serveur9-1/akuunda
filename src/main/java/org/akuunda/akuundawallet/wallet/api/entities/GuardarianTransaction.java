package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "guardarian_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuardarianTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Identifiants ---
    private Long externalTransactionId; // id Guardarian
    private Long partnerId;
    private String externalPartnerLinkId;

    // --- Informations client ---
    private String email;
    private String username;
    private String customerCountry;

    // --- Statuts ---
    private String status;
    private String statusDetails;

    // --- Informations monétaires ---
    private String fromCurrency;
    private String toCurrency;
    private String fromNetwork;
    private String toNetwork;
    private String fromCurrencyWithNetwork;
    private String toCurrencyWithNetwork;

    private Double fromAmount;
    private Double expectedFromAmount;
    private Double toAmount;
    private Double expectedToAmount;
    private Double fromAmountInEur;

    // --- Types de paiement ---
    private String depositType;
    private String payoutType;
    private String depositPaymentCategory;
    private String payoutPaymentCategory;

    // --- Détails supplémentaires ---
    private String outputHash;
    private String location;
    private Boolean customerPayoutAddressChangeable;

    // --- Payout info ---
    private String payoutAddress;
    private String payoutExtraId;

    // --- Breakdown estimations ---
    private Double estimatedExchangeRate;
    private Double partnerFeeAmount;
    private String partnerFeeCurrency;
    private String partnerFeePercentage;
    private Double networkFeeAmount;
    private String networkFeeCurrency;
    private Double serviceFeeAmount;
    private String serviceFeeCurrency;

    // --- Timestamps ---
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
