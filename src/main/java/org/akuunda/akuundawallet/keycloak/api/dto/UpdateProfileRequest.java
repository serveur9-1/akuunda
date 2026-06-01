package org.akuunda.akuundawallet.keycloak.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProfileRequest {

    // ─── Champs communs (Particulier + Entreprise) ─────────────────────────────
    private String firstName;
    private String lastName;
    private String email;
    private String address;

    @Schema(description = "Date de naissance. Formats acceptés : ISO `YYYY-MM-DD` ou `MM/DD/YYYY`.",
            example = "1990-05-15")
    private String dateNaissance;

    // ─── Champs spécifiques Entreprise ─────────────────────────────────────────
    private String siret;
    private String raisonSociale;

    // ─── KYC obligatoire pour les paiements YellowCard (compte Particulier) ────
    //
    // Note: le pays du marchand est porté par son wallet (`wallet.currency.country_code`,
    // exposé tel quel via `wallets[i].countryCode` dans la réponse de `getUser`). Il n'est
    // donc PAS demandé ici — la résolution est automatique côté serveur de paiement.

    /**
     * Type de pièce d'identité — valeurs supportées par YellowCard :
     * <ul>
     *     <li>{@code NATIONAL_ID} — Carte d'identité nationale</li>
     *     <li>{@code PASSPORT} — Passeport</li>
     *     <li>{@code DRIVERS_LICENSE} — Permis de conduire</li>
     *     <li>{@code VOTERS_CARD} — Carte d'électeur</li>
     *     <li>{@code BVN} — Bank Verification Number (Nigéria uniquement)</li>
     *     <li>{@code NIN} — National Identity Number (Nigéria uniquement)</li>
     * </ul>
     */
    @Schema(description = "Type de pièce d'identité (YellowCard).",
            allowableValues = {"NATIONAL_ID", "PASSPORT", "DRIVERS_LICENSE", "VOTERS_CARD", "BVN", "NIN"},
            example = "NATIONAL_ID",
            nullable = true)
    private String idType;

    @Schema(description = "Numéro de pièce d'identité associé à idType.",
            example = "CI001234567",
            nullable = true)
    private String idNumber;

    /**
     * Pièce d'identité secondaire — utile pour le Nigéria où YellowCard peut demander
     * un BVN/NIN en complément du NATIONAL_ID. Optionnel partout ailleurs.
     */
    @Schema(description = "Type de pièce d'identité secondaire (utilisé principalement au Nigéria).",
            allowableValues = {"NATIONAL_ID", "PASSPORT", "DRIVERS_LICENSE", "VOTERS_CARD", "BVN", "NIN"},
            example = "BVN",
            nullable = true)
    private String additionalIdType;

    @Schema(description = "Numéro de la pièce d'identité secondaire.",
            example = "22212345678",
            nullable = true)
    private String additionalIdNumber;
}
