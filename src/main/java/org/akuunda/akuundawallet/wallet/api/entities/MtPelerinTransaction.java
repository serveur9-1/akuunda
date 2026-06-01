package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mtpelerin_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MtPelerinTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiants MT Pelerin
    @Column(name = "merchant_oid", unique = true)
    private String merchantOid; // Identifiant de commande marchand (oid)
    
    @Column(name = "transaction_group_id")
    private String transactionGroupId;
    
    @Column(name = "reference")
    private String reference;

    // Statut
    @Column(nullable = false, length = 50)
    private String status; // "pending" | "failed" | "finished"

    // Montants
    @Column(name = "paid", length = 100)
    private String paid; // e.g. "1 BTC"
    
    @Column(name = "received", length = 100)
    private String received; // e.g. "100000 EUR"

    // Dates
    @Column(name = "creation_date")
    private LocalDateTime creationDate;
    
    @Column(name = "last_update")
    private LocalDateTime lastUpdate;
    
    @Column(name = "synced_at")
    private LocalDateTime syncedAt; // Date de synchronisation depuis l'API

    // Type de transaction
    @Column(name = "transaction_type", length = 20)
    private String transactionType; // "ONRAMP" | "OFFRAMP"

    // Informations utilisateur (optionnel)
    @Column(name = "username")
    private String username;
    
    @Column(name = "wallet_address")
    private String walletAddress;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) {
            syncedAt = LocalDateTime.now();
        }
    }
}

