package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sous-objet {@code source} de l'{@link OnRampRequest} — informations du payeur
 * (l'acheteur), à <b>saisir intégralement côté page checkout</b>.
 *
 * <p>Voir {@link OnRampRequest} pour la matrice d'orchestration complète. Règles
 * de remplissage essentielles :</p>
 * <ul>
 *     <li>Pour Mobile Money ({@code accountType = momo}) : {@code accountNumber}
 *         = numéro de téléphone Mobile Money de l'acheteur. {@code phoneNumber}
 *         est auto-aligné par le serveur ; ne pas le demander deux fois.</li>
 *     <li>Pour virement bancaire ({@code accountType = bank}) : {@code accountNumber}
 *         = IBAN, {@code accountBank} = nom de la banque émettrice.</li>
 *     <li>{@code accountName} = nom du titulaire du compte côté payeur (acheteur).</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Informations du payeur (acheteur). Saisies sur la page checkout publique.")
public class SourceDto {

    @Schema(description = "Nom du titulaire du compte côté acheteur.",
            example = "Awa Diallo", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountName;

    @Schema(description = "Type de compte / canal de paiement. `momo` pour Mobile Money, `bank` pour virement.",
            example = "momo", allowableValues = {"momo", "mobile_money", "bank"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountType;

    @Schema(description = "Identifiant opérateur Mobile Money (Orange, MTN, Wave, Moov, …). "
            + "Récupéré via GET /permanent-links/yellow-card/networks?countryCode=XX. "
            + "Obligatoire si `accountType` ∈ {momo, mobile_money}.",
            example = "8d18204e-b51f-4554-815d-71586d0dac13", nullable = true)
    private String networkId;

    @Schema(description = "Nom de l'opérateur (Orange / MTN / Wave / Moov). Optionnel : le serveur résout par `networkId`.",
            example = "Wave", nullable = true)
    private String networkName;

    @Schema(description = "Numéro de téléphone du payeur. **Auto-aligné** sur `accountNumber` "
            + "par le serveur pour les paiements momo — ne pas demander un deuxième champ à l'utilisateur.",
            example = "0700000000", nullable = true)
    private String phoneNumber;

    @Schema(description = "Numéro du compte source. Pour momo = numéro de téléphone Mobile Money ; "
            + "pour bank = IBAN. Champ obligatoire.",
            example = "0700000000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accountNumber;

    @Schema(description = "Banque émettrice (uniquement pour `accountType = bank`).",
            example = "ECOBANK", nullable = true)
    private String accountBank;
}
