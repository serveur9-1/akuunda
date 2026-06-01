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
@Schema(description = "Requête combinée pour créer un lien de paiement unique ET initier le paiement Meld en un seul appel")
public class CreateAndPayRequest {

    // --- Champs Create ---
    @NotBlank(message = "La description est obligatoire")
    @Schema(description = "Description/libellé du paiement", example = "Facture n°2024-001", required = true)
    private String description;

    @Schema(description = "Montant indicatif (optionnel, métadonnée uniquement)", example = "50.0", required = false)
    private Double amount;

    @Schema(description = "Code devise ISO (optionnel)", example = "EUR", required = false)
    private String currency;

    @Schema(description = "Date d'expiration (optionnel, 24h par défaut)", required = false)
    private LocalDateTime expiresAt;

    // --- Champs Pay ---
    @NotBlank(message = "Le fournisseur de paiement est obligatoire")
    @Schema(description = "Fournisseur de paiement (ex: MERCURYO, TRANSAK, UNLIMIT)", example = "MERCURYO", required = true)
    private String serviceProvider;

    @NotBlank(message = "La devise source est obligatoire")
    @Schema(description = "Code devise fiat source", example = "EUR", required = true)
    private String sourceCurrencyCode;

    @NotBlank(message = "Le montant source est obligatoire")
    @Schema(description = "Montant en fiat à payer", example = "10", required = true)
    private String sourceAmount;

    @NotBlank(message = "Le code pays est obligatoire")
    @Schema(description = "Code pays du payeur", example = "FR", required = true)
    private String countryCode;

    @Schema(description = "Numéro de téléphone du payeur (optionnel)", example = "+33652242825", required = false)
    private String payerPhone;

    @Schema(description = "Nom du payeur (optionnel)", example = "Jean Dupont", required = false)
    private String payerName;

    @Schema(description = "Email du payeur (optionnel)", example = "jean@example.com", required = false)
    private String payerEmail;
}
