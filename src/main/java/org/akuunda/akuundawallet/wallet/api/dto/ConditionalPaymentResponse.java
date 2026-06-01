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
@Schema(description = "Réponse contenant les informations d'un paiement conditionnel")
public class ConditionalPaymentResponse {

    @Schema(description = "ID du paiement conditionnel", example = "1")
    private Long id;

    @Schema(description = "Code unique du paiement", example = "CP-20250101-ABC123")
    private String paymentCode;

    @Schema(description = "Username du client", example = "0033612108828")
    private String clientUsername;

    @Schema(description = "Username du vendeur", example = "hotel123")
    private String vendorUsername;

    @Schema(description = "Type de prestation", example = "HOTEL")
    private String serviceType;

    @Schema(description = "Description de la prestation", example = "Réservation chambre d'hôtel - 2 nuits")
    private String description;

    @Schema(description = "Montant du paiement", example = "100.0")
    private Double amount;

    @Schema(description = "Devise", example = "USDC")
    private String currency;

    @Schema(description = "Statut : pending_condition, condition_validated, released, refunded, refunded_partial")
    private String status;

    @Schema(description = "Adresse du smart contract de séquestre")
    private String escrowContractAddress;

    @Schema(description = "Hash de la transaction de dépôt")
    private String depositTransactionHash;

    @Schema(description = "Hash de la transaction de libération")
    private String releaseTransactionHash;

    @Schema(description = "Hash de la transaction de remboursement")
    private String refundTransactionHash;

    @Schema(description = "Date prévue de début de la prestation")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime serviceStartDate;

    @Schema(description = "Date réelle de début de la prestation")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime serviceActualStartDate;

    @Schema(description = "Date limite pour annulation sans pénalité")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancellationDeadline;

    @Schema(description = "Montant retenu en cas de remboursement partiel")
    private Double retainedAmount;

    @Schema(description = "Montant remboursé au client")
    private Double refundedAmount;

    @Schema(description = "Raison de l'annulation")
    private String cancellationReason;

    @Schema(description = "URL du QR code pour validation")
    private String qrCodeUrl;

    @Schema(description = "Token du QR code")
    private String qrCodeToken;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "Montant effectivement reçu par le prestataire (en devise fiat du vendeur)", example = "25000.0")
    private Double receivedAmount;

    @Schema(description = "Devise du montant reçu par le prestataire", example = "XOF")
    private String receivedCurrency;

    @Schema(description = "Date/heure de libération des fonds vers le prestataire")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime releasedAt;

    @Schema(description = "Nom complet du prestataire/vendeur (créateur du paiement)", example = "David Kouassi")
    private String creatorName;

    @Schema(description = "Username du créateur du paiement conditionnel", example = "hotel123")
    private String creatorUsername;

    @Schema(description = "Nom complet du payeur/client", example = "Aman Koffip")
    private String payerName;

    @Schema(description = "Numéro de téléphone du payeur/client", example = "+33612108828")
    private String payerPhone;

    @Schema(description = "Adresse email du payeur/client", example = "aman@yahoo.com")
    private String payerEmail;

    @Schema(description = "Montant payé par le client dans sa devise locale (avant conversion en USDC)", example = "26.61")
    private Double sourceAmount;

    @Schema(description = "Devise source du paiement du client", example = "EUR")
    private String sourceCurrency;

    @Schema(description = "Adresse du wallet blockchain du marchand sur lequel les fonds ont été envoyés", example = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18")
    private String vendorWalletAddress;

    @Schema(description = "Message d'erreur (présent uniquement en cas d'échec 4xx/5xx)")
    private String error;
}
