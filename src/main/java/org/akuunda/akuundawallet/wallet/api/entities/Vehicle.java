package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles", indexes = {
    @Index(name = "idx_vehicle_provider", columnList = "provider_id"),
    @Index(name = "idx_vehicle_type", columnList = "type")
})
@Builder
@Getter
@Setter
@ToString(exclude = "provider")
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String type; // sedan, suv, van, minibus

    @Column(nullable = false)
    private String brand; // Toyota, Mercedes, etc.

    @Column(nullable = false)
    private String model; // Camry, S-Class, etc.

    @Column(name = "plate_number", nullable = false)
    private String plateNumber;

    @Column(name = "max_passengers", nullable = false)
    private Integer maxPassengers;

    @Column(name = "max_luggage", columnDefinition = "INTEGER DEFAULT 2")
    @Builder.Default
    private Integer maxLuggage = 2;

    @Column(name = "price_per_km", nullable = false)
    private Double pricePerKm;

    @Column(name = "base_price", nullable = false)
    private Double basePrice;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "EUR";

    @Column(length = 2000)
    private String images; // JSON array of image URLs

    @Column(name = "has_air_conditioning", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean hasAirConditioning = true;

    @Column(name = "has_wifi", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean hasWifi = false;

    @Column(name = "is_available", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean isAvailable = true;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private TransportProvider provider;

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
