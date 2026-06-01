package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Local record of a virtual IBAN created on TransFi. Persisted right after POST /v3/iban/create-iban
 * succeeds when an Akuunda {@code username} was attached to the request. Lets us list a user's IBANs
 * without round-tripping to TransFi.
 */
@Entity
@Table(name = "transfi_ibans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransfiIbanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iban_id", unique = true, length = 100)
    private String ibanId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "transfi_user_id", length = 100)
    private String transfiUserId;

    @Column(length = 50)
    private String iban;

    @Column(length = 20)
    private String bic;

    @Column(length = 10)
    private String currency;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "account_holder_name", length = 200)
    private String accountHolderName;

    @Column(length = 50)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
