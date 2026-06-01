package org.akuunda.akuundawallet.wallet.api.dto.booking;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour créer une réservation d'hôtel")
public class CreateHotelBookingRequest {

    @NotNull(message = "L'ID de l'hôtel est obligatoire")
    @Schema(description = "ID de l'hôtel", example = "1")
    private Long hotelId;

    @NotNull(message = "L'ID de la chambre est obligatoire")
    @Schema(description = "ID de la chambre", example = "1")
    private Long roomId;

    @NotNull(message = "La date d'arrivée est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Date d'arrivée (check-in)", example = "2025-04-20T14:00:00")
    private LocalDateTime checkInDate;

    @NotNull(message = "La date de départ est obligatoire")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Date de départ (check-out)", example = "2025-04-22T11:00:00")
    private LocalDateTime checkOutDate;

    @NotNull(message = "Le nombre de voyageurs est obligatoire")
    @Min(value = 1, message = "Au moins 1 voyageur requis")
    @Schema(description = "Nombre de voyageurs", example = "2")
    private Integer numberOfGuests;

    @Schema(description = "Nombre de chambres", example = "1")
    private Integer numberOfRooms = 1;

    @Schema(description = "Demandes spéciales", example = "Chambre non-fumeur, vue sur mer")
    private String specialRequests;
}
