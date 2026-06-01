package org.akuunda.akuundawallet.wallet.api.dto.booking;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.akuunda.akuundawallet.wallet.api.enums.BookingStatus;
import org.akuunda.akuundawallet.wallet.api.enums.BookingType;
import org.akuunda.akuundawallet.wallet.api.enums.TransportType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse contenant les informations d'une réservation")
public class BookingResponse {

    private Long id;
    private String reference;
    private BookingType type;
    private BookingStatus status;

    // Utilisateur
    private String userId;
    private String userName;
    private String userPhone;
    private String userEmail;

    // Prestataire
    private String providerId;
    private String providerName;

    // Montants
    private Double amount;
    private Double serviceFee;
    private Double totalAmount;
    private String currency;

    // QR Code & Escrow
    private String qrCode;
    private String qrCodeUrl;
    private Long conditionalPaymentId;
    private String conditionalPaymentCode;
    private Boolean fundsLocked;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fundsLockedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fundsReleasedAt;

    // Dates
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime acceptedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime rejectedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelledAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    // Détails Hôtel
    private HotelBookingDetails hotelDetails;

    // Détails Transport
    private TransportBookingDetails transportDetails;

    // Notes
    private String userNote;
    private String providerNote;
    private String cancellationReason;
    private String rejectionReason;

    // Message d'erreur
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HotelBookingDetails {
        private Long hotelId;
        private String hotelName;
        private String hotelAddress;
        private String hotelImageUrl;
        private Long roomId;
        private String roomName;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime checkInDate;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime checkOutDate;

        private Integer numberOfNights;
        private Integer numberOfGuests;
        private Integer numberOfRooms;
        private Double pricePerNight;
        private String specialRequests;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransportBookingDetails {
        private Long providerId;
        private String providerName;
        private String providerPhotoUrl;
        private Long vehicleId;
        private String vehicleType;
        private String vehicleBrand;
        private String vehicleModel;
        private TransportType transportType;
        private String pickupAddress;
        private Double pickupLatitude;
        private Double pickupLongitude;
        private String dropoffAddress;
        private Double dropoffLatitude;
        private Double dropoffLongitude;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime pickupDateTime;

        private String flightNumber;
        private Integer numberOfPassengers;
        private Integer numberOfLuggage;
        private Double estimatedDistance;
        private Integer estimatedDuration;
        private String specialRequests;
    }
}
