package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationDto {

    private Long id;
    private String type;              // CREDIT ou DEBIT
    private String designation;       // Constante harmonisée (MELD_ON_RAMP, INTERNAL_TRANSFER_SENT, etc.)
    private String transactionType;   // Label affiché : Dépôt, Retrait, Envoi, Reçu, Encaissement, Paiement
    private String provider;          // YellowCard, Meld, Guardarian, Interne
    private String status;            // VALIDEE, REJETEE, EN_COURS, EXPIREE, REMBOURSEE, PENDING_CONDITION
    private String username;
    private String devise;            // Devise locale affichée à l'utilisateur
    private Double amount;            // Montant en devise locale
    private Double convertedAmount;   // Montant converti en devise locale (pour affichage)
    private String counterpartDisplayName;

    // ── Montant provider ──
    private Double providerAmount;    // Montant USDC échangé avec le provider
    private String providerDevise;    // USDC, USDT, etc.

    // ── Montant original (legacy, pour rétro-compatibilité) ──
    private String originalDevise;
    private Double originalAmount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    private String operationHash;
}
