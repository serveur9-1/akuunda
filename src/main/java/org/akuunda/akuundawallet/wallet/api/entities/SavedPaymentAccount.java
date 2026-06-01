package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "saved_payment_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedPaymentAccount implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "operator_name", nullable = false)
    private String operatorName;

    @Column(name = "operator_type", nullable = false, length = 50)
    private String operatorType;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "account_bank")
    private String accountBank;

    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode;

    @Column(name = "network_id", nullable = false)
    private String networkId;

    @Column(name = "id_type", nullable = false, length = 50)
    private String idType;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column(name = "additional_id_type", length = 50)
    private String additionalIdType;

    @Column(name = "additional_id_number")
    private String additionalIdNumber;

    @Column(name = "date_of_birth", nullable = false)
    private String dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String email;

    @Column(name = "is_default", nullable = false)
    private boolean defaultFlag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
