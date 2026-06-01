package org.akuunda.akuundawallet.wallet.api.dto.booking;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.akuunda.akuundawallet.wallet.api.enums.TransportType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour créer une réservation de transport")
public class CreateTransportBookingRequest {

    @NotNull(message = "L'ID du prestataire est obligatoire")
    @Schema(description = "ID du prestataire de transport", example = "1")
    private Long providerId;

    @NotNull(message = "L'ID du véhicule est obligatoire")
    @Schema(description = "ID du véhicule", example = "1")
    private Long vehicleId;

    @NotNull(message = "Le type de transport est obligatoire")
    @Schema(description = "Type de transport", example = "AIRPORT")
    private TransportType transportType;

    @NotBlank(message = "L'adresse de départ est obligatoire")
    @Schema(description = "Adresse de départ", example = "Aéroport Paris CDG, Terminal 2E")
    private String pickupAddress;

    @Schema(description = "Latitude du point de départ", example = "49.0097")
    private Double pickupLatitude;

    @Schema(description = "Longitude du point de départ", example = "2.5479")
    private Double pickupLongitude;

    @NotBlank(message = "L'adresse d'arrivée est obligatoire")
    @Schema(description = "Adresse d'arrivée", example = "15 Rue de Rivoli, 75001 Paris")
    private String dropoffAddress;

    @Schema(description = "Latitude du point d'arrivée", example = "48.8566")
    private Double dropoffLatitude;

    @Schema(description = "Longitude du point d'arrivée", example = "2.3522")
    private Double dropoffLongitude;

    @NotNull(message = "La date et heure de prise en charge est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Date et heure de prise en charge", example = "2025-04-20T14:30:00")
    private LocalDateTime pickupDateTime;

    @Schema(description = "Numéro de vol (pour transfert aéroport)", example = "AF1234")
    private String flightNumber;

    @NotNull(message = "Le nombre de passagers est obligatoire")
    @Min(value = 1, message = "Au moins 1 passager requis")
    @Schema(description = "Nombre de passagers", example = "2")
    private Integer numberOfPassengers;

    @Schema(description = "Nombre de bagages", example = "3")
    private Integer numberOfLuggage = 0;

    @Schema(description = "Demandes spéciales", example = "Siège bébé nécessaire")
    private String specialRequests;
}
