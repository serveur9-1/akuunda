package org.akuunda.akuundawallet.backoffice.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionDto {
    private Long id;
    private String reference;       // operationHash
    private String designation;     // code interne (INTERNAL_TRANSFER_SENT, MELD_ON_RAMP, …)
    private String transactionType; // label affiché (Envoi, Dépôt, Retrait, …)
    private String type;            // CREDIT ou DEBIT
    private String status;          // VALIDEE, REJETEE, EN_COURS, EXPIREE, REMBOURSEE, PENDING_CONDITION
    private String expediteur;      // username
    private String destinataire;    // counterpartDisplayName ou counterpartUsername
    private Double amount;          // montant en devise locale
    private String currency;        // devise locale (EUR, XOF, …)
    private Double convertedAmount;
    private Double originalAmount;
    private String originalCurrency;
    private Double providerAmount;
    private String providerCurrency;
    private String provider;
    private String createdAt;
    private String updatedAt;
}
