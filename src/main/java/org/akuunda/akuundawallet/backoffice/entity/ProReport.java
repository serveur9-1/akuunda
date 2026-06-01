package org.akuunda.akuundawallet.backoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pro_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProReport {

    @Id
    private UUID id;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(nullable = false)
    private String name;

    /** TRANSACTIONS, PAYMENTS, VOLUME */
    @Column(nullable = false)
    private String type;

    /** COMPLETED, FAILED */
    @Column(nullable = false)
    private String status;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "total_amount")
    private Double totalAmount;

    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
