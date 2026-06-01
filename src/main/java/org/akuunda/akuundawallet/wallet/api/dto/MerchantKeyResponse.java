package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Réponse de création d'une clé API marchand.
 * Le `webhookSecret` n'est exposé qu'une seule fois ici.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Clé API marchand créée")
public class MerchantKeyResponse {

    @Schema(description = "Identifiant interne", example = "12")
    private Long id;

    @Schema(description = "Nom usuel", example = "Boutique principale")
    private String name;

    @Schema(description = "Mode : \"live\" ou \"test\"", example = "live")
    private String mode;

    @Schema(description = "Clé publique d'API", example = "sk_live_a1b2c3d4...")
    private String apiKey;

    @Schema(description = "Secret pour vérifier la signature des webhooks (affiché une seule fois)",
            example = "whsec_a1b2c3d4...")
    private String webhookSecret;

    @Schema(description = "URL webhook par défaut")
    private String webhookUrl;

    @Schema(description = "URL de redirection succès par défaut")
    private String returnUrl;

    @Schema(description = "URL de redirection annulation par défaut")
    private String cancelUrl;

    /**
     * Username marchand Akuunda Pay : c'est l'identifiant utilisé pour mapper le compte du marchand
     * chez nos opérateurs (Meld : {@code externalCustomerId}, intégrations partenaires).
     * Renvoyé à la création pour que le marchand sache quelle valeur passer aux providers
     * dans les flux qui pré-remplissent des champs.
     */
    @Schema(description = "Username marchand Akuunda Pay (login). Sert d'externalCustomerId Meld et de clé de mapping côté providers.",
            example = "002250759146858")
    private String merchantUsername;

    /**
     * Identifiant technique du marchand côté Akuunda Pay (Keycloak {@code sub} / colonne
     * {@code users.user_id}). Utilisé comme customerUID YellowCard.
     */
    @Schema(description = "Identifiant technique marchand (Keycloak sub / users.user_id). CustomerUID YellowCard.",
            example = "8d3a1c52-7e91-4dbb-b9e0-3fa1c2c1d0ff")
    private String merchantUserId;

    @Schema(description = "Slug marchand de référence (lien permanent par défaut)", example = "boutique-mama-coco-ab12cd")
    private String merchantSlug;

    @Schema(description = "Email du marchand (référence)", example = "boutique@example.com")
    private String merchantEmail;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
