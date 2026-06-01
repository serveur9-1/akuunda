package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Payload de paiement YellowCard On-Ramp pour la route publique
 * {@code POST /api/internal/v1/permanent-links/session/{sessionCode}/pay}.
 *
 * <p><b>Orchestration des champs.</b> Le payload est rempli par <em>deux</em>
 * acteurs différents. Pour intégrer proprement le checkout (page hébergée
 * <em>ou</em> intégration sur un site marchand), il faut respecter qui
 * fournit quoi :</p>
 *
 * <table>
 *   <tr><th>Champ</th><th>Rempli par</th><th>Comment</th></tr>
 *   <tr><td>{@code amount} / {@code currency} / {@code channelId}</td>
 *       <td>Marchand → repris du lien permanent (currency) + sélection acheteur (channelId)</td>
 *       <td>Lecture {@code GET /m/{slug}} + sélection UI</td></tr>
 *   <tr><td>{@code country}</td><td>Acheteur</td>
 *       <td>Sélecteur alimenté par {@code GET /m/{slug}/countries}</td></tr>
 *   <tr><td>{@code reason} / {@code forceAccept} / {@code directSettlement}</td>
 *       <td>Frontend (constantes : « other », true, true)</td><td>—</td></tr>
 *   <tr><td>{@code recipient.*}</td><td><b>Auto-rempli côté serveur</b> à partir
 *       du profil du marchand (créateur du lien)</td>
 *       <td>Toute valeur envoyée par le client est ignorée</td></tr>
 *   <tr><td>{@code source.accountType}</td><td>Frontend</td>
 *       <td>Déduit du channel choisi (« momo » ou « bank »)</td></tr>
 *   <tr><td>{@code source.networkId}</td><td>Acheteur</td>
 *       <td>Sélecteur alimenté par {@code GET /yellow-card/networks?countryCode=XX}</td></tr>
 *   <tr><td>{@code source.accountNumber}</td><td>Acheteur</td>
 *       <td>Numéro de téléphone Mobile Money (momo) ou IBAN (bank)</td></tr>
 *   <tr><td>{@code source.accountName}</td><td>Acheteur</td>
 *       <td>Nom du titulaire du compte</td></tr>
 *   <tr><td>{@code source.phoneNumber}</td><td><b>Auto-aligné côté serveur</b>
 *       sur {@code accountNumber} pour momo — ne pas demander deux fois à l'utilisateur</td>
 *       <td>—</td></tr>
 * </table>
 *
 * <p>Les champs marqués <b>Acheteur</b> manquants déclenchent un
 * {@code 422 BUYER_INFO_INCOMPLETE} avant tout appel à YellowCard.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Payload YellowCard On-Ramp pour le checkout public d'un lien permanent.")
public class OnRampRequest {

        @Schema(description = "Identifiant du canal YellowCard sélectionné par l'acheteur. "
                + "À récupérer via GET /permanent-links/yellow-card/channels?countryCode=XX.",
                example = "b621bf4f-0c60-4884-88a1-75f7a56b1938", requiredMode = Schema.RequiredMode.REQUIRED)
        private String channelId;

        @Schema(description = "Montant en devise du lien. Doit correspondre à `amount` retourné par /m/{slug}.",
                example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
        private double amount;

        @Schema(description = "Devise du lien (ISO-4217). Doit correspondre à `currency` retourné par /m/{slug}.",
                example = "XOF", requiredMode = Schema.RequiredMode.REQUIRED)
        private String currency;

        @Schema(description = "Pays ISO-2 sélectionné par l'acheteur (XOF/XAF couvrent plusieurs pays). "
                + "À récupérer via GET /m/{slug}/countries.",
                example = "CI", requiredMode = Schema.RequiredMode.REQUIRED)
        private String country;

        @Schema(description = "Motif libre de la transaction. Valeur frontend par défaut : `other`.",
                example = "other", defaultValue = "other")
        private String reason;

        @Schema(description = "Frontend constante. Laisser à `true` pour le flux checkout public.",
                defaultValue = "true")
        private boolean forceAccept;

        @Schema(description = "Frontend constante. Laisser à `true` pour livraison directe vers le wallet CREATE2 de la session.",
                defaultValue = "true")
        private boolean directSettlement;

        @Schema(description = "Type de ramp (interne YellowCard). Optionnel — le serveur force la bonne valeur si absent.",
                nullable = true)
        private String rampType;

        @Schema(description = "**Rempli automatiquement côté serveur** à partir du profil du marchand "
                + "(créateur du lien). Toute valeur envoyée par le client est ignorée. C'est ici que se "
                + "retrouvent name, phone, email, dob, country, idType, idNumber du marchand après auto-fill.",
                nullable = true)
        private RecipientDto recipient;

        @Schema(description = "**À remplir par l'acheteur** sur la page checkout : type de canal (`momo` ou "
                + "`bank`), opérateur (`networkId` pour momo), numéro de compte (`accountNumber`), "
                + "nom du titulaire (`accountName`). Pour momo, `phoneNumber` est auto-aligné sur "
                + "`accountNumber` côté serveur — ne pas le demander deux fois à l'utilisateur.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private SourceDto source;

        @Schema(description = "Type de client côté recipient : `individual` ou `institution`. "
                + "Auto-déterminé par le serveur en fonction du profil marchand (raisonSociale ≠ null = institution).",
                nullable = true)
        private String customerType;

}
