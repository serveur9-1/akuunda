package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;          // CREDIT ou DEBIT
    private String status;        // VALIDEE, REJETEE, EN_COURS, EXPIREE, REMBOURSEE, PENDING_CONDITION
    private String username;
    private String devise;        // Devise locale de l'utilisateur (EUR, XOF, XAF, etc.)
    private Double amount;        // Montant en devise locale
    private String designation;   // Constante harmonisée (MELD_ON_RAMP, YELLOWCARD_OFF_RAMP, INTERNAL_TRANSFER_SENT, etc.)
    private String transactionType; // Label affiché : Dépôt, Retrait, Envoi, Reçu, Encaissement, Paiement
    private String provider;      // YellowCard, Meld, Guardarian, Interne
    private Double convertedAmount;
    private Double originalAmount;
    private String originalDevise;

    // ── Montant provider (USDC reçu/envoyé au provider) ──
    private Double providerAmount;   // Montant en USDC (ou autre crypto) échangé avec le provider
    private String providerDevise;   // Devise du provider (USDC, USDT, etc.)

    // ── Contrepartie (pour affichage historique maquette) ──
private String counterpartUsername;      // Username de l'autre partie
private String counterpartDisplayName;   // "Cédric", "Hôtel Monogaga", "+225 07 77 06 21 60"

    // ── Champs legacy (à ignorer pour les nouvelles opérations) ──
    private Double walletBalanceBefore;
    private Double walletBalanceAfter;
    private Double walletDeviseBalanceBefore;
    private Double walletDeviseBalanceAfter;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private String operationHash;
}
