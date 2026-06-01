package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms", indexes = {
    @Index(name = "idx_room_hotel", columnList = "hotel_id"),
    @Index(name = "idx_room_is_available", columnList = "is_available")
})
@Builder
@Getter
@Setter
@ToString(exclude = "hotel")
@NoArgsConstructor
@AllArgsConstructor
public class Room implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // "Chambre Standard", "Suite Deluxe"

    @Column(length = 1000)
    private String description;

    @Column(name = "price_per_night", nullable = false)
    private Double pricePerNight;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "max_guests", nullable = false)
    private Integer maxGuests;

    @Column(length = 1000)
    private String amenities; // JSON array

    @Column(length = 2000)
    private String images; // JSON array of image URLs

    @Column(name = "is_available", columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "total_rooms", columnDefinition = "INTEGER DEFAULT 1")
    @Builder.Default
    private Integer totalRooms = 1;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

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
