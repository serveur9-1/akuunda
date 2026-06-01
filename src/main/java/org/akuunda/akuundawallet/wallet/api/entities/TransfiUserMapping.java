package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Maps an Akuunda wallet {@code username} (phone) to a TransFi {@code userId} (UX-...).
 * Auto-populated when POST /transfi/users/individual or /transfi/users/business succeeds
 * with a {@code username} on the request. Used to translate {@code username} → TransFi
 * {@code userId} on every endpoint that expects a TransFi user identifier.
 */
@Entity
@Table(name = "transfi_users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransfiUserMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "transfi_user_id", nullable = false, unique = true, length = 100)
    private String transfiUserId;

    @Column(name = "user_type", nullable = false, length = 20)
    @Builder.Default
    private String userType = "individual";

    @Column(name = "kyc_status", length = 50)
    private String kycStatus;

    @Column(name = "kyb_status", length = 50)
    private String kybStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
