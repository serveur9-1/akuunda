package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "kyrrex_sepa_iban_state", indexes = {
        @Index(name = "idx_kyrrex_sepa_username", columnList = "username"),
        @Index(name = "idx_kyrrex_sepa_status", columnList = "status"),
        @Index(name = "idx_kyrrex_sepa_provider_instrument", columnList = "provider_id,instrument")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KyrrexSepaIbanState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(nullable = false, length = 50)
    private String instrument;

    @Column(name = "instrument_id", length = 255)
    private String instrumentId;

    @Column(name = "iban", length = 64)
    private String iban;

    @Column(name = "status", nullable = false, length = 30)
    private String status; // ACTIVE | CLOSED | UNKNOWN

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
