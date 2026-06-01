package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
@Schema(description = "Réponse contenant les informations d'un lien de paiement unique (one-time)")
public class OneTimePaymentLinkResponse {

    @Schema(description = "ID du lien", example = "1")
    private Long id;

    @Schema(description = "Code unique du lien (8 caractères)", example = "a1b2c3d4")
    private String uniqueCode;

    @Schema(description = "Code du paiement conditionnel associé (ex: CP-EVKCPOBH)", example = "CP-EVKCPOBH")
    private String paymentCode;

    @Schema(description = "Description du paiement", example = "Facture n°2024-001")
    private String description;

    @Schema(description = "Montant du paiement", example = "21.35")
    private Double amount;

    @Schema(description = "Devise du paiement", example = "EUR")
    private String currency;

    @Schema(description = "Montant source en crypto (USDC)", example = "14.50")
    private Double sourceAmount;

    @Schema(description = "Devise source crypto", example = "USDC")
    private String sourceCurrency;

    @Schema(description = "Statut du lien (CREATED, PENDING, PAID, EXPIRED, CANCELLED, FAILED)", example = "CREATED")
    private String status;

    @Schema(description = "Date d'expiration")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Numéro de téléphone du payeur", example = "+2250700123456")
    private String payerPhone;

    @Schema(description = "Nom du payeur", example = "Jean Dupont")
    private String payerName;

    @Schema(description = "Email du payeur", example = "jean.dupont@example.com")
    private String payerEmail;

    @Schema(description = "Date de paiement")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paidAt;

    @Schema(description = "Username du créateur", example = "002250759146858")
    private String creatorUsername;

    @Schema(description = "Nom complet du créateur (marchand)", example = "Jean Dupont")
    private String creatorName;

    @Schema(description = "Date de création")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Date de dernière mise à jour")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "Adresse CREATE2 du wallet temporaire de réception")
    private String create2WalletAddress;

    @Schema(description = "Payment ID on-chain (bytes32)")
    private String paymentIdBytes32;

    @Schema(description = "Type de lien (SIMPLE ou CONDITIONAL)", example = "CONDITIONAL")
    private String linkType;

    @Schema(description = "Type de service (pour liens conditionnels)", example = "HOTEL")
    private String serviceType;

    @Schema(description = "Date de début de la prestation")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime serviceStartDate;

    @Schema(description = "Date limite d'annulation")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancellationDeadline;

    @Schema(description = "URL du QR code (pour liens conditionnels payés)")
    private String qrCodeUrl;

    @Schema(description = "Token du QR code")
    private String qrCodeToken;

    @Schema(description = "ID du paiement conditionnel associé")
    private Long conditionalPaymentId;
}
