package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.enums.BookingStatus;
import org.akuunda.akuundawallet.wallet.api.enums.BookingType;
import org.akuunda.akuundawallet.wallet.api.enums.TransportType;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

// ... reste du code identique

@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_booking_reference", columnList = "reference"),
    @Index(name = "idx_booking_user", columnList = "user_id"),
    @Index(name = "idx_booking_provider_user", columnList = "provider_user_id"),
    @Index(name = "idx_booking_status", columnList = "status"),
    @Index(name = "idx_booking_type", columnList = "type"),
    @Index(name = "idx_booking_conditional_payment", columnList = "conditional_payment_id")
})
@Builder
@Getter
@Setter
@ToString(exclude = {"user", "providerUser", "hotel", "room", "transportProvider", "vehicle"})
@NoArgsConstructor
@AllArgsConstructor
public class Booking implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String reference; // BK-20250410-XXXXXX

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingType type; // HOTEL, TRANSPORT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // ── Utilisateur (Client) ─────────────────────────────────────────────────
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_phone")
    private String userPhone;

    @Column(name = "user_email")
    private String userEmail;

    // ── Prestataire ──────────────────────────────────────────────────────────
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_user_id", nullable = false)
    private Users providerUser;

    @Column(name = "provider_name")
    private String providerName;

    // ── Montants ─────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private Double amount;

    @Column(name = "service_fee", columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    @Builder.Default
    private Double serviceFee = 0.0;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "EUR";

    // ── QR Code & Escrow ─────────────────────────────────────────────────────
    @Column(name = "qr_code", length = 500)
    private String qrCode;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    @Column(name = "conditional_payment_id")
    private Long conditionalPaymentId;

    @Column(name = "conditional_payment_code", length = 50)
    private String conditionalPaymentCode;

    @Column(name = "funds_locked", columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private Boolean fundsLocked = false;

    @Column(name = "funds_locked_at")
    private LocalDateTime fundsLockedAt;

    @Column(name = "funds_released_at")
    private LocalDateTime fundsReleasedAt;

    // ── Dates ────────────────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // Expiration de la demande

    // ── Détails Hôtel ────────────────────────────────────────────────────────
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "check_in_date")
    private LocalDateTime checkInDate;

    @Column(name = "check_out_date")
    private LocalDateTime checkOutDate;

    @Column(name = "number_of_nights")
    private Integer numberOfNights;

    @Column(name = "number_of_guests")
    private Integer numberOfGuests;

    @Column(name = "number_of_rooms", columnDefinition = "INTEGER DEFAULT 1")
    @Builder.Default
    private Integer numberOfRooms = 1;

    // ── Détails Transport ────────────────────────────────────────────────────
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transport_provider_id")
    private TransportProvider transportProvider;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", length = 20)
    private TransportType transportType;

    @Column(name = "pickup_address", length = 500)
    private String pickupAddress;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "dropoff_address", length = 500)
    private String dropoffAddress;

    @Column(name = "dropoff_latitude")
    private Double dropoffLatitude;

    @Column(name = "dropoff_longitude")
    private Double dropoffLongitude;

    @Column(name = "pickup_date_time")
    private LocalDateTime pickupDateTime;

    @Column(name = "flight_number", length = 20)
    private String flightNumber;

    @Column(name = "number_of_passengers")
    private Integer numberOfPassengers;

    @Column(name = "number_of_luggage")
    private Integer numberOfLuggage;

    @Column(name = "estimated_distance")
    private Double estimatedDistance; // km

    @Column(name = "estimated_duration")
    private Integer estimatedDuration; // minutes

    // ── Notes ────────────────────────────────────────────────────────────────
    @Column(name = "user_note", length = 1000)
    private String userNote;

    @Column(name = "provider_note", length = 1000)
    private String providerNote;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (reference == null) {
            reference = generateReference();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(24); // Expire après 24h par défaut
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generateReference() {
        return "BK-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now())
                + "-" + java.util.UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
