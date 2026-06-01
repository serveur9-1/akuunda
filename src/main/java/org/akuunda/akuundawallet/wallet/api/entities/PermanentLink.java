package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "permanent_links", indexes = {
    @Index(name = "idx_permanent_link_merchant_slug", columnList = "merchant_slug", unique = true)
})
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermanentLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_slug", nullable = false, unique = true, length = 100)
    private String merchantSlug;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private Users creator;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = true)
    private Double amount;

    @Column(nullable = true, length = 10)
    private String currency;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private Integer totalSessions;

    @Column(nullable = false)
    private Integer totalCompletedPayments;

    @Column(nullable = false)
    private Double totalAmountReceived;

    @Column(name = "partner_contract_code", length = 50)
    private String partnerContractCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (totalSessions == null) {
            totalSessions = 0;
        }
        if (totalCompletedPayments == null) {
            totalCompletedPayments = 0;
        }
        if (totalAmountReceived == null) {
            totalAmountReceived = 0.0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
