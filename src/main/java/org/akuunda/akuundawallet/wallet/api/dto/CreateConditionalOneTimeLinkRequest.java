package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour créer un lien de paiement conditionnel (escrow) à usage unique")
public class CreateConditionalOneTimeLinkRequest {

    @NotBlank(message = "La description est obligatoire")
    @Schema(description = "Description de la prestation", example = "Réservation chambre d'hôtel - 2 nuits", required = true)
    private String description;

    @Schema(description = "Montant du paiement", example = "50000.0")
    private Double amount;

    @Schema(description = "Code devise ISO", example = "XOF")
    private String currency;

    @NotBlank(message = "Le type de service est obligatoire")
    @Schema(description = "Type de prestation", example = "HOTEL", allowableValues = {"HOTEL", "TRAVEL_AGENCY", "TOURISM", "RENTAL", "DELIVERY"})
    private String serviceType;

    @Schema(description = "Date prévue de début de la prestation", example = "2025-12-25T14:00:00")
    private LocalDateTime serviceStartDate;

    @Schema(description = "Date limite d'annulation sans pénalité", example = "2025-12-23T14:00:00")
    private LocalDateTime cancellationDeadline;

    @Schema(description = "Date d'expiration du lien de paiement", example = "2025-12-31T23:59:59")
    private LocalDateTime expiresAt;
}
