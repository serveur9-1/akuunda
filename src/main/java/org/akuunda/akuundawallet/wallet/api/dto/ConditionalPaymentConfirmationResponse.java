package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse de confirmation après paiement conditionnel - contient le QR code à télécharger")
public class ConditionalPaymentConfirmationResponse {

    @Schema(description = "Code unique du lien de paiement")
    private String uniqueCode;

    @Schema(description = "Code du paiement conditionnel")
    private String paymentCode;

    @Schema(description = "Statut du paiement")
    private String status;

    @Schema(description = "Description de la prestation")
    private String description;

    @Schema(description = "Montant payé")
    private Double amount;

    @Schema(description = "Devise")
    private String currency;

    @Schema(description = "Type de service")
    private String serviceType;

    @Schema(description = "Date prévue de début de la prestation")
    private LocalDateTime serviceStartDate;

    @Schema(description = "Date d'expiration du lien de paiement")
    private LocalDateTime expiresAt;

    @Schema(description = "URL du QR code pour validation")
    private String qrCodeUrl;

    @Schema(description = "Token du QR code")
    private String qrCodeToken;

    @Schema(description = "Prénom du vendeur/prestataire")
    private String vendorFirstName;

    @Schema(description = "Nom de famille du vendeur/prestataire")
    private String vendorLastName;

    @Schema(description = "Message de confirmation")
    private String confirmationMessage;

    @Schema(description = "Erreur éventuelle")
    private String error;
}
