package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_api_keys", indexes = {
    @Index(name = "idx_merchant_api_key", columnList = "api_key", unique = true)
})
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantApiKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key", nullable = false, unique = true, length = 100)
    private String apiKey;

    @Column(name = "api_secret", nullable = false, length = 200)
    private String apiSecret;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private Users merchant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "permanent_link_id", nullable = false)
    private PermanentLink permanentLink;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    @Column(name = "cancel_url", length = 500)
    private String cancelUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** Mode opératoire : "live" ou "test". */
    @Column(name = "mode", length = 10)
    private String mode;

    /** Nom usuel de la clé (libellé). */
    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (mode == null || mode.isBlank()) mode = "live";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
