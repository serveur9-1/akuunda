package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ... reste du code identique

@Entity
@Table(name = "transport_providers", indexes = {
    @Index(name = "idx_transport_provider_owner", columnList = "owner_id"),
    @Index(name = "idx_transport_provider_city", columnList = "city"),
    @Index(name = "idx_transport_provider_is_available", columnList = "is_available")
})
@Builder
@Getter
@Setter
@ToString(exclude = {"vehicles", "owner"})
@NoArgsConstructor
@AllArgsConstructor
public class TransportProvider implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "photo_url")
    private String photoUrl;

    private String phone;
    private String email;

    private String city;
    private String country;

    @Column(columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    @Builder.Default
    private Double rating = 0.0;

    @Column(name = "review_count", columnDefinition = "INTEGER DEFAULT 0")
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "completed_trips", columnDefinition = "INTEGER DEFAULT 0")
    @Builder.Default
    private Integer completedTrips = 0;

    @Column(name = "service_areas", length = 1000)
    private String serviceAreas; // JSON array: ["Paris", "Lyon", "Marseille"]

    @Column(name = "service_types", length = 500)
    private String serviceTypes; // JSON array: ["AIRPORT", "CITY", "INTERCITY"]

    @Column(name = "is_verified", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_available", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean isAvailable = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Users owner;

    @JsonManagedReference
    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Vehicle> vehicles = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
