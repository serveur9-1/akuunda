package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour créer un paiement conditionnel")
public class ConditionalPaymentRequest {

    @NotBlank(message = "Le username du vendeur est obligatoire")
    @Schema(
            description = "Username du vendeur/prestataire qui recevra le paiement",
            example = "0033612108828"
    )
    private String vendorUsername;

    @NotBlank(message = "Le type de service est obligatoire")
    @Schema(
            description = "Type de prestation : HOTEL, TRAVEL_AGENCY, TOURISM, RENTAL, DELIVERY",
            example = "HOTEL"
    )
    private String serviceType;

    @NotBlank(message = "La description est obligatoire")
    @Schema(
            description = "Description de la prestation",
            example = "Réservation chambre d'hôtel - 2 nuits"
    )
    private String description;

    @NotNull(message = "Le montant est obligatoire")
    @Positive(message = "Le montant doit être positif")
    @Schema(
            description = "Montant du paiement (en devise locale si currency est fourni, sinon en USDC)",
            example = "50000.0"
    )
    private Double amount;

    @Schema(
            description = "Devise du montant (XOF, EUR, etc.). Si non fourni, le montant est considéré en USDC. Le système convertira automatiquement en USDC.",
            example = "XOF"
    )
    private String currency;

    @Schema(
            description = "Date prévue de début de la prestation",
            example = "2025-12-25T14:00:00"
    )
    private LocalDateTime serviceStartDate;

    @Schema(
            description = "Date limite pour annulation sans pénalité",
            example = "2025-12-23T14:00:00"
    )
    private LocalDateTime cancellationDeadline;
}

