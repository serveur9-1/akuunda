package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_emergency_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEmergencyCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String encryptedString;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    /**
     * {@code true} si les 5 caractères mémorisés par l'utilisateur sont les 5 derniers chiffres
     * de son {@code mobile_phone} (les 5 caractères eux-mêmes ne sont jamais stockés).
     */
    @Column(name = "uses_phone_last5", nullable = false)
    @Builder.Default
    private boolean usesPhoneLast5 = false;
}
