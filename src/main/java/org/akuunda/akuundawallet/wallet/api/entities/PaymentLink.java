package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_links", uniqueConstraints = {
    @UniqueConstraint(columnNames = "uniqueCode")
})
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String uniqueCode; // Code unique court (ex: "gn1mb")

    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private Users creator; // Utilisateur qui a créé le lien

    @Column(nullable = false, length = 500)
    private String description; // Description/libellé du paiement

    @Column(nullable = true)
    private Double amount; // Montant fixe (null = montant libre)

    @Column(nullable = true, length = 10)
    private String currency; // Devise (XOF, EUR, USD, etc.) - null = devise choisie par le payeur

    @Column(nullable = false)
    private Boolean isActive; // Si le lien est actif

    @Column(nullable = true)
    private LocalDateTime expiresAt; // Date d'expiration (null = pas d'expiration)

    @Column(nullable = false)
    private Integer totalPayments; // Nombre total de paiements reçus

    @Column(nullable = false)
    private Double totalAmountReceived; // Montant total reçu

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalPayments == null) {
            totalPayments = 0;
        }
        if (totalAmountReceived == null) {
            totalAmountReceived = 0.0;
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

