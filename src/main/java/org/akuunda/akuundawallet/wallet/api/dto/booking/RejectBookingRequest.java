package org.akuunda.akuundawallet.wallet.api.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour refuser une réservation")
public class RejectBookingRequest {

    @NotBlank(message = "La raison du refus est obligatoire")
    @Schema(description = "Raison du refus", example = "Plus de disponibilité pour ces dates")
    private String reason;
}
