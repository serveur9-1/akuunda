package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entité représentant un QR code pour la validation d'un paiement conditionnel.
 */
@Entity
@Table(name = "qr_codes", indexes = {
    @Index(name = "idx_qr_code_payment", columnList = "conditional_payment_id"),
    @Index(name = "idx_qr_code_token", columnList = "token", unique = true)
})
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QRCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Token unique du QR code (utilisé pour générer le QR code)
     */
    @Column(nullable = false, unique = true, length = 100)
    private String token;

    /**
     * Paiement conditionnel associé
     */
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conditional_payment_id", nullable = false)
    private ConditionalPayment conditionalPayment;

    /**
     * URL du QR code (générée à partir du token)
     */
    @Column(nullable = false, length = 500)
    private String qrCodeUrl;

    /**
     * Statut du QR code :
     * - generated : QR code généré, non scanné
     * - scanned : QR code scanné, prestation validée
     * - expired : QR code expiré
     */
    @Column(nullable = false, length = 50)
    private String status;

    /**
     * Date de génération du QR code
     */
    @Column(nullable = false)
    private LocalDateTime generatedAt;

    /**
     * Date de scan du QR code
     */
    @Column(nullable = true)
    private LocalDateTime scannedAt;

    /**
     * Date d'expiration du QR code
     */
    @Column(nullable = true)
    private LocalDateTime expiresAt;

    /**
     * Utilisateur qui a scanné le QR code (peut être le client ou le vendeur selon le contexte)
     */
    @Column(length = 255)
    private String scannedBy;

    @PrePersist
    protected void onCreate() {
        generatedAt = LocalDateTime.now();
        if (status == null) {
            status = "generated";
        }
    }
}

