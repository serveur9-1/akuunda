package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.akuunda.akuundawallet.wallet.api.dto.external.RecipientDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.SourceDto;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour créer un lien conditionnel YellowCard (étape 1 — génération du lien uniquement, sans appel YellowCard)")
public class CreateConditionalLinkYcRequest {

    // --- Conditional link fields ---
    @NotBlank(message = "La description est obligatoire")
    @Schema(description = "Description/libellé du paiement", example = "Réservation hôtel Ivoire", required = true)
    private String description;

    @Schema(description = "Montant indicatif (optionnel, métadonnée uniquement)", example = "50000.0")
    private Double amount;

    @Schema(description = "Code devise ISO (optionnel)", example = "XOF")
    private String currency;

    @Schema(description = "Date d'expiration du lien (optionnel, 24h par défaut)")
    private LocalDateTime expiresAt;

    @NotBlank(message = "Le type de service est obligatoire")
    @Schema(description = "Type de prestation", example = "HOTEL",
            allowableValues = {"HOTEL", "TRAVEL_AGENCY", "TOURISM", "RENTAL", "DELIVERY"})
    private String serviceType;

    @Schema(description = "Date prévue de début de la prestation (check-in)", example = "2026-03-20T14:00:00")
    private LocalDateTime serviceStartDate;

    @Schema(description = "Date limite d'annulation sans pénalité", example = "2026-03-18T14:00:00")
    private LocalDateTime cancellationDeadline;

    // --- Payer info ---
    @Schema(description = "Numéro de téléphone du payeur (optionnel)", example = "+2250701020304")
    private String payerPhone;

    @Schema(description = "Nom du payeur (optionnel)", example = "Kouamé Konan")
    private String payerName;

    @Schema(description = "Email du payeur (optionnel)", example = "kouame@example.com")
    private String payerEmail;

    // --- YellowCard data stored for later confirmation (step 2) ---
    @NotBlank(message = "Le channelId YellowCard est obligatoire")
    @Schema(description = "ID du canal de paiement YellowCard (ex: MoMo CI, Wave CI)", example = "ch_01ABCDE12345")
    private String channelId;

    @NotBlank(message = "Le pays YellowCard est obligatoire")
    @Schema(description = "Code pays ISO YellowCard (ex: CI, CM, GH)", example = "CI")
    private String country;

    @NotBlank(message = "La devise locale YellowCard est obligatoire")
    @Schema(description = "Code devise locale YellowCard (ex: XOF, XAF, GHS)", example = "XOF")
    private String localCurrency;

    @Schema(description = "Motif du paiement YellowCard", example = "Réservation hôtel")
    private String reason;

    @Schema(description = "Informations sur le destinataire (payeur) pour YellowCard")
    private RecipientDto recipient;

    @Schema(description = "Informations sur la source de paiement (compte MoMo, Wave, etc.) pour YellowCard")
    private SourceDto source;
}
