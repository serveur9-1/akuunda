package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Credentials Kyrrex stockés par utilisateur Akuunda.
 *
 * <p>Deux niveaux :</p>
 * <ul>
 *   <li><b>Permanents</b> ({@code accessKey}, {@code secretKey}) — registration.
 *       Utilisés <b>uniquement</b> pour POST /sessions.</li>
 *   <li><b>Session</b> ({@code sessionAccessKey}, {@code sessionSecretKey}) — temporaires.
 *       Utilisés pour <b>tous les autres appels API</b>.</li>
 * </ul>
 *
 * <p>Tous les champs *Key sont chiffrés AES-256-GCM.</p>
 */
@Entity
@Table(name = "kyrrex_user_credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KyrrexUserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "kyrrex_member_uid", length = 100)
    private String kyrrexMemberUid;

    // ═══════════ CREDENTIALS PERMANENTS (registration) ═══════════

    @Column(name = "access_key", nullable = false, length = 512)
    private String accessKey;

    @Column(name = "secret_key", nullable = false, length = 512)
    private String secretKey;

    // ═══════════ CREDENTIALS DE SESSION (temporaires) ═══════════

    @Column(name = "session_access_key", length = 512)
    private String sessionAccessKey;

    @Column(name = "session_secret_key", length = 512)
    private String sessionSecretKey;

    @Column(name = "session_expire_at")
    private Instant sessionExpireAt;

    // ═══════════ METADATA ═══════════

    @Column(name = "kyc_verified_at")
    private Instant kycVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // ═══════════ DERIVED HELPERS (pas de colonne en base) ═══════════

    /**
     * Actif = non révoqué.
     * Pas de colonne "active" en base — dérivé de revoked_at.
     */
    public boolean isActive() {
        return revokedAt == null;
    }

    /**
     * KYC vérifié = kyc_verified_at non null.
     * Pas de colonne "kyc_verified" en base — dérivé de kyc_verified_at.
     */
    public boolean isKycVerified() {
        return kycVerifiedAt != null;
    }

    /**
     * Vérifie si une session active existe.
     * Marge de 30 secondes avant expiration.
     */
    public boolean hasActiveSession() {
        return sessionAccessKey != null
                && sessionSecretKey != null
                && sessionExpireAt != null
                && Instant.now().plusSeconds(30).isBefore(sessionExpireAt);
    }

    /** Efface les credentials de session */
    public void clearSession() {
        this.sessionAccessKey = null;
        this.sessionSecretKey = null;
        this.sessionExpireAt = null;
    }

    /** Vérifie si le compte est utilisable (actif + non révoqué) */
    public boolean isUsable() {
        return isActive();
    }
}
