package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entité représentant un paiement conditionnel (escrow) via smart contract.
 * Le paiement est bloqué jusqu'à validation par scan de QR code.
 */
@Entity
@Table(name = "conditional_payments", indexes = {
    @Index(name = "idx_conditional_payment_status", columnList = "status"),
    @Index(name = "idx_conditional_payment_client", columnList = "client_id"),
    @Index(name = "idx_conditional_payment_vendor", columnList = "vendor_id")
})
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ConditionalPayment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Code unique pour identifier le paiement conditionnel
     */
    @Column(nullable = false, unique = true, length = 50)
    private String paymentCode;

    /**
     * Client qui effectue le paiement (null pour les payeurs externes sans compte)
     */
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = true)
    private Users client;

    /**
     * Vendeur/prestataire qui reçoit le paiement
     */
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Users vendor;

    /**
     * Type de prestation (HOTEL, TRAVEL_AGENCY, TOURISM, RENTAL, DELIVERY)
     */
    @Column(nullable = false, length = 50)
    private String serviceType;

    /**
     * Description de la prestation
     */
    @Column(nullable = false, length = 1000)
    private String description;

    /**
     * Montant du paiement (en USDC)
     */
    @Column(nullable = false)
    private Double amount;

    /**
     * Devise du montant (USDC par défaut)
     */
    @Column(nullable = false, length = 10)
    private String currency;

    /**
     * Statut du paiement :
     * - pending_condition : Paiement bloqué, en attente de validation
     * - condition_validated : QR code scanné, prestation commencée
     * - released : Fonds libérés vers le vendeur
     * - refunded : Remboursement total au client
     * - refunded_partial : Remboursement partiel + retenue pour le vendeur
     */
    @Column(nullable = false, length = 50)
    private String status;

    /**
     * Adresse du smart contract de séquestre
     */
    @Column(length = 255)
    private String escrowContractAddress;

    /**
     * Hash de la transaction blockchain pour le dépôt dans le smart contract
     */
    @Column(length = 255)
    private String depositTransactionHash;

    /**
     * Hash de la transaction blockchain pour la libération des fonds
     */
    @Column(length = 255)
    private String releaseTransactionHash;

    /**
     * Hash de la transaction blockchain pour le remboursement
     */
    @Column(length = 255)
    private String refundTransactionHash;

    /**
     * Date prévue de début de la prestation
     */
    @Column(nullable = true)
    private LocalDateTime serviceStartDate;

    /**
     * Date réelle de début de la prestation (après scan QR)
     */
    @Column(nullable = true)
    private LocalDateTime serviceActualStartDate;

    /**
     * Date limite pour annulation sans pénalité
     */
    @Column(nullable = true)
    private LocalDateTime cancellationDeadline;

    /**
     * Montant retenu en cas de remboursement partiel
     */
    @Column(nullable = true)
    private Double retainedAmount;

    /**
     * Montant remboursé au client
     */
    @Column(nullable = true)
    private Double refundedAmount;

    /**
     * Raison de l'annulation/remboursement
     */
    @Column(length = 500)
    private String cancellationReason;

    /**
     * ID de l'opération DEBIT pour le client
     */
    @Column(nullable = true)
    private Long clientOperationId;

    /**
     * ID de l'opération CREDIT pour le vendeur (après libération)
     */
    @Column(nullable = true)
    private Long vendorOperationId;

    /**
     * ID de l'opération CREDIT pour le client (en cas de remboursement)
     */
    @Column(nullable = true)
    private Long refundOperationId;

    /**
     * Wallet intermédiaire (wallet de service Akuunda Pay)
     */
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "intermediate_wallet_id", nullable = true)
    private Wallet intermediateWallet;

    /**
     * Wallet du client
     */
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_wallet_id", nullable = true)
    private Wallet clientWallet;

    /**
     * Wallet du vendeur
     */
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_wallet_id", nullable = true)
    private Wallet vendorWallet;

    // ── NOUVEAUX CHAMPS ──────────────────────────────────────────────────

    /**
     * Montant effectivement reçu par le prestataire (en devise fiat du vendeur)
     */
    @Column(name = "received_amount", nullable = true)
    private Double receivedAmount;

    /**
     * Devise du montant reçu par le prestataire
     */
    @Column(name = "received_currency", length = 10, nullable = true)
    private String receivedCurrency;

    /**
     * Date/heure de libération des fonds vers le prestataire
     */
    @Column(name = "released_at", nullable = true)
    private LocalDateTime releasedAt;

    // ─────────────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "pending_condition";
        }
        if (currency == null) {
            currency = "USDC";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
