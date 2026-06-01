package org.akuunda.akuundawallet.wallet.api.dto.partner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Crée un contrat partenaire ET initie le paiement provider en un seul appel.
 * Retourne directement le lien provider (Meld widget URL ou YellowCard redirect).
 */
@Data
public class CreatePartnerPaymentRequest {

    // ── Contrat ────────────────────────────────────────────────────────────

    @NotBlank(message = "Le nom du contrat est requis")
    private String name;

    private String description;

    @NotBlank(message = "Le type de service est requis")
    private String serviceType;

    @Pattern(regexp = "QR_CODE|MANUAL|WEBHOOK|REMOTE_CONFIRMATION|OTP|DUAL_CONFIRMATION|TIME_BASED|DISPUTE_WINDOW|GEOLOCATION|ADMIN_APPROVAL|DYNAMIC_ASSIGNMENT",
             message = "triggerType invalide")
    private String triggerType = "MANUAL";

    @DecimalMin(value = "0.0", message = "Le taux de pénalité doit être >= 0")
    @DecimalMax(value = "1.0", message = "Le taux de pénalité doit être <= 1.0")
    private Double cancellationPenaltyRate = 0.0;

    @Min(0)
    private Integer freeCancellationHours = 24;

    @Min(1)
    private Integer autoReleaseHours;

    @Min(1)
    private Integer disputeWindowHours;

    @Valid
    private List<BeneficiaryRequest> beneficiaries;

    // ── Paiement ───────────────────────────────────────────────────────────

    /** Username Akuunda si le client a un compte — sinon laisser vide */
    private String clientUsername;

    /** Téléphone du client (WhatsApp) — utilisé si pas de compte Akuunda */
    private String clientPhone;

    /** Email du client — utilisé si pas de compte Akuunda */
    private String clientEmail;

    @NotNull(message = "Le montant est requis")
    @DecimalMin(value = "0.01", message = "Le montant doit être > 0")
    private Double amount;

    private String currency = "USDC";

    private Double localAmount;
    private String localCurrency;

    private LocalDateTime serviceStartDate;

    private String webhookUrl;
    private String webhookSecret;

    // ── Provider (optionnel — si fourni, initie directement la session provider) ──

    /**
     * "MELD" ou "YELLOWCARD".
     * Si null, retourne le qrUrl de la page de choix (qr.akuunda-pay.io/PCP-xxx).
     */
    private String provider;

    // Meld
    private String sourceCurrencyCode;
    private String sourceAmount;
    private String countryCode;
    private String serviceProvider;
    private String clientIpAddress;

    // YellowCard
    @Valid
    private YellowCardPaymentInfo yellowCard;

    /**
     * Informations YellowCard — seul le pays est requis.
     * Le recipient est auto-renseigné depuis le profil clientUsername.
     * Le client choisit son réseau et saisit ses infos source sur link.akuunda-pay.io.
     */
    @Data
    public static class YellowCardPaymentInfo {
        @NotBlank(message = "Le pays est requis")
        private String country;
    }
}
