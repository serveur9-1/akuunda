package org.akuunda.akuundawallet.wallet.api.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour annuler une réservation")
public class CancelBookingRequest {

    @NotBlank(message = "La raison de l'annulation est obligatoire")
    @Schema(description = "Raison de l'annulation", example = "Changement de plans")
    private String reason;
}
