package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pays disponible pour un paiement, retourné par l'endpoint public
 * {@code GET /api/internal/v1/permanent-links/m/{slug}/countries}.
 *
 * <p>Source de vérité : table {@code country_currency} (filtrée sur la devise du
 * lien et sur {@code is_activated = true}). Aucune valeur n'est codée en dur :
 * tout pays présent en base avec la bonne devise apparaîtra ici dès qu'il sera
 * activé.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Pays acceptant la devise d'un lien permanent (pour le sélecteur pays côté checkout public).")
public class CountryOptionResponse {

    @Schema(description = "Code pays ISO-2 (alpha-2).", example = "CI")
    private String code;

    @Schema(description = "Nom du pays en français.", example = "Côte d'Ivoire")
    private String name;

    @Schema(description = "Indicatif téléphonique international.", example = "225")
    private Integer callingCode;

    @Schema(description = "Continent du pays.", example = "Africa")
    private String continentName;

    @Schema(description = "URL du drapeau (PNG ou SVG).",
            example = "https://flagcdn.com/w320/ci.png", nullable = true)
    private String flagUrl;

    @Schema(description = "Code devise associé (toujours identique à la devise du lien).",
            example = "XOF")
    private String currencyCode;
}
