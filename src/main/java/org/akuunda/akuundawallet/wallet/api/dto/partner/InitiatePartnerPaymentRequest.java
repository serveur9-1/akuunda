package org.akuunda.akuundawallet.wallet.api.dto.partner;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InitiatePartnerPaymentRequest {

    /** Username Akuunda si le client a un compte — sinon laisser vide */
    private String clientUsername;

    /** Téléphone du client (WhatsApp) — utilisé si pas de compte Akuunda */
    private String clientPhone;

    /** Email du client — utilisé si pas de compte Akuunda */
    private String clientEmail;

    @NotNull(message = "Le montant est requis")
    @DecimalMin(value = "0.01", message = "Le montant doit être > 0")
    private Double amount;

    /** USDC par défaut */
    private String currency = "USDC";

    /** Montant en devise locale (informatif) */
    private Double localAmount;
    private String localCurrency;

    /** PIN du client — non requis, le client fait un simple virement USDC vers l'adresse CREATE2 */
    private String clientPin;

    /** Date prévue de début de service */
    private LocalDateTime serviceStartDate;

    /** URL de webhook si triggerType = WEBHOOK */
    private String webhookUrl;
    private String webhookSecret;
}
