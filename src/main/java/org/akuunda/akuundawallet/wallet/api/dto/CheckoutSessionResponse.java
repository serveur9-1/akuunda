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
@Schema(description = "Informations d'un checkout pour affichage sur la page de paiement")
public class CheckoutSessionResponse {

    private String checkoutCode;
    private Double amount;
    private String currency;
    private String reference;
    private String description;
    private String status;
    /** {@code live} ou {@code test} (sandbox) — pour affichage sur la page de paiement hébergée. */
    private String mode;
    private String merchantSlug;
    private String merchantName;

    @Schema(description = "Username marchand Akuunda Pay (Meld / résolution wallet). À passer aux flux provider qui attendent ce login marchand.")
    private String merchantUsername;

    @Schema(description = "Identifiant technique marchand (Keycloak sub). Sens aligné avec les réponses lien permanent / session.")
    private String merchantUserId;

    /**
     * Code pays ISO-2 du marchand (ex. {@code CI}, {@code SN}, {@code CM}). Provient de
     * {@code users.country_currency.country_code}. Permet à la page checkout publique de
     * choisir un pays par défaut <em>sémantiquement correct</em> pour les devises
     * multi-pays (XOF, XAF) au lieu d'un mapping hard-codé côté frontend.
     */
    @Schema(description = "Code pays ISO-2 du marchand. Sert de défaut pour le pays de paiement quand la devise (XOF, XAF) couvre plusieurs pays.",
            example = "SN")
    private String merchantCountry;

    /**
     * Pays de paiement cible fixé par le marchand (ISO 3166-1 alpha-2).
     * Si présent, la page de paiement l'utilise directement sans sélecteur.
     */
    @Schema(description = "Pays de paiement imposé par le marchand (ex. CI). Absent = sélection automatique ou par l'acheteur.", example = "CI")
    private String paymentCountry;

    private String callbackUrl;
    private String cancelUrl;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
