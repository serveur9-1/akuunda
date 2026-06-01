package org.akuunda.akuundawallet.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Création d'une clé API marchand. Auth : JWT (utilisateur connecté au dashboard).
 * Tous les champs sont optionnels — défauts raisonnables.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Création d'une clé API marchand")
public class MerchantKeyCreateRequest {

    /**
     * Username Akuunda Pay du marchand pour lequel la clé est créée — <strong>autoritatif</strong>.
     *
     * <p>Tous les appels API Akuunda sont authentifiés auprès de Keycloak via un compte de service
     * master (par défaut {@code akuunda1}). Le JWT seul ne permet donc pas d'identifier un
     * marchand : ce champ <strong>fait foi</strong> pour désigner le compte cible.</p>
     *
     * <p>Formats acceptés : login Akuunda Pay, email, ou téléphone ({@code +33…}, {@code 0033…},
     * {@code 06…}). Insensible à la casse.</p>
     *
     * <p>Règles côté serveur :</p>
     * <ul>
     *     <li><strong>Fourni</strong> et résolu vers un vrai marchand → utilisé.</li>
     *     <li><strong>Fourni</strong> mais valant le compte de service ({@code akuunda1}) →
     *         <strong>422 MERCHANT_USERNAME_RESERVED</strong>.</li>
     *     <li><strong>Fourni</strong> mais inconnu → <strong>422 MERCHANT_NOT_FOUND</strong>.</li>
     *     <li><strong>Absent</strong> et JWT = compte de service → <strong>400
     *         MERCHANT_USERNAME_REQUIRED</strong>.</li>
     *     <li><strong>Absent</strong> et JWT = vrai marchand (dashboard Pro où le marchand est
     *         lui-même authentifié) → on prend le marchand du JWT (rétro-compat).</li>
     * </ul>
     */
    @Schema(description = "Username / email / téléphone du marchand cible. **Requis** quand l'appel passe "
            + "par le compte de service public Akuunda — c'est ce champ qui désigne le marchand "
            + "(et non le JWT).",
            example = "002250759146858",
            nullable = true)
    private String username;

    @Schema(description = "Nom usuel de la clé (ex: \"Boutique principale\")", example = "Boutique principale")
    private String name;

    @Schema(description = "URL par défaut où recevoir les webhooks de paiement",
            example = "https://boutique.com/api/webhooks/akuunda")
    private String webhookUrl;

    @Schema(description = "URL de redirection par défaut après paiement réussi",
            example = "https://boutique.com/success")
    private String returnUrl;

    @Schema(description = "URL de redirection par défaut si l'utilisateur annule",
            example = "https://boutique.com/cancel")
    private String cancelUrl;

    @Schema(description = "Mode : \"live\" (production) ou \"test\" (sandbox)",
            example = "live", allowableValues = {"live", "test"}, defaultValue = "live")
    private String mode;
}
