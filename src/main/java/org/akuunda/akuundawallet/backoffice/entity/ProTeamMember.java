package org.akuunda.akuundawallet.backoffice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pro_team_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProTeamMember {

    @Id
    private UUID id;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    /** ADMIN, MANAGER, OPERATOR, VIEWER */
    @Column(nullable = false)
    private String role;

    private String department;

    /** INVITED, ACTIVE, INACTIVE, SUSPENDED */
    @Column(nullable = false)
    private String status;

    @Column(name = "two_factor_enabled")
    private boolean twoFactorEnabled;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
