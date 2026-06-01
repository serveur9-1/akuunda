package org.akuunda.akuundawallet.wallet.api.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requête pour accepter une réservation")
public class AcceptBookingRequest {

    @Schema(description = "Note du prestataire", example = "Réservation confirmée. À bientôt !")
    private String note;
}
