package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_pins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPin implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String generatedString;

    @Column(nullable = false)
    private String encryptedString;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}


