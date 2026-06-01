package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entité représentant les règles de remboursement pour un type de prestation.
 * Permet de définir les pénalités d'annulation selon les délais.
 */
@Entity
@Table(name = "conditional_payment_rules", indexes = {
    @Index(name = "idx_rule_service_type", columnList = "service_type")
})
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ConditionalPaymentRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type de prestation (HOTEL, TRAVEL_AGENCY, TOURISM, RENTAL, DELIVERY)
     */
    @Column(nullable = false, length = 50)
    private String serviceType;

    /**
     * Nombre d'heures avant le début de la prestation pour cette règle
     * Ex: 48 = annulation 48h avant
     */
    @Column(nullable = false)
    private Integer hoursBeforeService;

    /**
     * Pourcentage de pénalité retenu (ex: 0.0 = 0%, 0.20 = 20%)
     */
    @Column(nullable = false)
    private Double penaltyPercentage;

    /**
     * Montant fixe de pénalité (alternative au pourcentage, null si on utilise le pourcentage)
     */
    @Column(nullable = true)
    private Double fixedPenaltyAmount;

    /**
     * Description de la règle
     */
    @Column(length = 500)
    private String description;

    /**
     * Si la règle est active
     */
    @Column(nullable = false)
    private Boolean isActive;

    /**
     * Vendeur propriétaire de cette règle (null = règle globale par défaut)
     */
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id", nullable = true)
    private org.akuunda.akuundawallet.keycloak.api.entities.Users vendor;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

