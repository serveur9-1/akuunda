package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Contrat partenaire : définit les règles de redistribution des fonds
 * pour un partenaire donné. Permet à chaque partenaire de configurer
 * ses propres conditions sans développement spécifique.
 */
@Entity
@Table(name = "partner_contracts")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartnerContract implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code unique du contrat (ex: PARTNER_HOPITAL_CI_001) */
    @Column(nullable = false, unique = true, length = 50)
    private String contractCode;

    /** keccak256(contractCode) — ID utilisé on-chain dans registerConfig() */
    @Column(length = 66)
    private String onChainConfigId;

    /** Username Akuunda Pay du partenaire propriétaire du contrat */
    @Column(nullable = false)
    private String partnerUsername;

    /** Nom lisible du contrat */
    @Column(nullable = false)
    private String name;

    /** Description du cas d'usage */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Type de service (HOTEL, MEDICAL, TRANSPORT, DELIVERY, CUSTOM…) */
    @Column(nullable = false, length = 100)
    private String serviceType;

    /**
     * Comment la redistribution est déclenchée :
     * QR_CODE  : scan d'un QR code par le client/vendeur
     * MANUAL   : appel API explicite par le partenaire
     * WEBHOOK  : callback entrant d'un système tiers
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String triggerType = "QR_CODE";

    /**
     * Type de lien de paiement autorisé pour ce contrat.
     * ONE_TIME_ONLY : seul le partner peut initier un paiement via API (pas de lien permanent public)
     * PERMANENT_ONLY : lien permanent réutilisable uniquement
     * BOTH (défaut)  : les deux sont disponibles
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String paymentLinkType = "BOTH";

    /**
     * Taux de pénalité en cas d'annulation (0.0 = 0%, 0.2 = 20%, 1.0 = 100%).
     * Le montant penalité est reversé selon les règles du contrat.
     */
    @Column(nullable = false)
    @Builder.Default
    private Double cancellationPenaltyRate = 0.0;

    /** Heures avant le service pendant lesquelles l'annulation est gratuite */
    @Column
    @Builder.Default
    private Integer freeCancellationHours = 24;

    // ── TIME_BASED / DISPUTE_WINDOW ──────────────────────────────────────────

    /** Heures après serviceStartDate (ou dépôt) avant libération automatique */
    @Column
    private Integer autoReleaseHours;

    /** Fenêtre en heures pendant laquelle le client peut contester (DISPUTE_WINDOW) */
    @Column
    private Integer disputeWindowHours;

    // ── GEOLOCATION ──────────────────────────────────────────────────────────

    /** Latitude attendue du lieu de service */
    @Column
    private Double expectedLatitude;

    /** Longitude attendue du lieu de service */
    @Column
    private Double expectedLongitude;

    /** Rayon de tolérance en mètres (défaut : 200m) */
    @Column
    @Builder.Default
    private Double geolocationRadiusMeters = 200.0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Liste ordonnée des bénéficiaires et leurs pourcentages */
    // EAGER conservé : UsdcTransferEventListener passe l'entité à CompletableFuture.runAsync() (thread différent).
    // L'entité devient détachée — accéder à une collection LAZY lancerait LazyInitializationException silencieuse.
    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("executionOrder ASC")
    @Builder.Default
    private List<PartnerContractBeneficiary> beneficiaries = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Vérifie que la somme des pourcentages ne dépasse pas 100% */
    public boolean isBeneficiaryAllocationValid() {
        double total = beneficiaries.stream()
                .mapToDouble(PartnerContractBeneficiary::getPercentage)
                .sum();
        return total <= 1.0 && total > 0.0;
    }
}
