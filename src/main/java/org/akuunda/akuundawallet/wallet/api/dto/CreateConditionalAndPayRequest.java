package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête combinée pour créer un lien de paiement conditionnel (escrow) ET initier le paiement en un seul appel")
public class CreateConditionalAndPayRequest extends CreateAndPayRequest {

    @NotBlank(message = "Le type de service est obligatoire")
    @Schema(description = "Type de prestation", example = "HOTEL",
            allowableValues = {"HOTEL", "TRAVEL_AGENCY", "TOURISM", "RENTAL", "DELIVERY"})
    private String serviceType;

    @Schema(description = "Date prévue de début de la prestation (check-in)",
            example = "2026-03-20T14:00:00")
    private LocalDateTime serviceStartDate;

    @Schema(description = "Date limite d'annulation sans pénalité",
            example = "2026-03-18T14:00:00")
    private LocalDateTime cancellationDeadline;
}
