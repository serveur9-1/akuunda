package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Bénéficiaire d'un contrat partenaire.
 * Définit qui reçoit quelle part du montant lors de la libération des fonds.
 */
@Entity
@Table(name = "partner_contract_beneficiaries")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartnerContractBeneficiary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private PartnerContract contract;

    /**
     * Type de bénéficiaire :
     * VENDOR   : Prestataire principal (hôpital, hôtel, etc.)
     * PARTNER  : Partenaire Akuunda Pay
     * OPERATOR : Commission opérationnelle/technique
     * CUSTOM   : Tout autre acteur
     */
    @Column(nullable = false, length = 50)
    private String beneficiaryType;

    /** Libellé lisible (ex: "Hôpital Saint-Luc", "Commission Akuunda") */
    @Column(nullable = false)
    private String label;

    /**
     * Adresse wallet Polygon du bénéficiaire (prioritaire sur username).
     * Si renseignée, le transfert se fait directement vers cette adresse.
     */
    @Column(length = 255)
    private String walletAddress;

    /**
     * Username Akuunda Pay du bénéficiaire.
     * Utilisé si walletAddress est absent pour résoudre le wallet.
     */
    @Column(length = 255)
    private String username;

    /**
     * Part du montant total (0.0 à 1.0).
     * Ex: 0.70 = 70% du montant va à ce bénéficiaire.
     */
    @Column(nullable = false)
    private Double percentage;

    /** Ordre d'exécution des transferts (1 = premier) */
    @Column(nullable = false)
    @Builder.Default
    private Integer executionOrder = 1;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
