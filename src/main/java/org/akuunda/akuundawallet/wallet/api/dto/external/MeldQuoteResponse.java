package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // <-- ignore les champs inconnus
public class MeldQuoteResponse {

    // ==========================================
    // Providers recommandés par plage de montant (ordre de priorité)
    // ==========================================

    /**
     * Providers recommandés pour les montants entre 10 et 14 000 EUR.
     * L'ordre est important : UNLIMIT → MERCURYO → BANXA → GUARDARIAN
     */
    public static final List<String> PROVIDERS_SMALL_MEDIUM_ORDERED = List.of("UNLIMIT", "MERCURYO", "BANXA", "GUARDARIAN");

    /**
     * Providers recommandés pour les montants entre 14 000 et 80 000 EUR.
     */
    public static final List<String> PROVIDERS_LARGE_ORDERED = List.of("TRANSFI");

    /**
     * Providers recommandés pour les pays africains.
     */
    public static final List<String> PROVIDERS_AFRICA_ORDERED = List.of("FONBNK");

    // ==========================================
    // Codes pays africains (ISO 2 lettres)
    // ==========================================

    /**
     * Ensemble des codes pays africains en ISO 2 lettres.
     */
    public static final Set<String> AFRICAN_COUNTRY_CODES = Set.of(
            "DZ", "AO", "BJ", "BW", "BF", "BI", "CV", "CM", "CF", "TD", "KM", "CG", "CD", "CI",
            "DJ", "EG", "GQ", "ER", "SZ", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR",
            "LY", "MG", "MW", "ML", "MR", "MU", "MA", "MZ", "NA", "NE", "NG", "RW", "ST", "SN",
            "SC", "SL", "SO", "ZA", "SS", "SD", "TZ", "TG", "TN", "UG", "ZM", "ZW"
    );

    // ==========================================
    // Seuils de montants (en EUR)
    // ==========================================

    /**
     * Montant minimum pour les recommendations (10 EUR).
     */
    public static final double MINIMUM_AMOUNT = 10.0;

    /**
     * Seuil entre petits/moyens montants et gros montants (14 000 EUR).
     */
    public static final double SMALL_MEDIUM_THRESHOLD = 14_000.0;

    /**
     * Seuil maximal pour les gros montants (80 000 EUR).
     */
    public static final double LARGE_THRESHOLD = 80_000.0;

    // ==========================================
    // Données
    // ==========================================

    private List<MeldQuote> quotes;

    // ==========================================
    // Méthode publique : Appliquer les recommandations
    // ==========================================

    /**
     * Applique la logique de recommandation et trie les quotes.
     * Les quotes recommandées apparaissent en premier, puis triées par ordre de priorité.
     */
    public void applyRecommendations() {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        // Étape 1 : Marquer chaque quote comme recommandée ou non
        for (MeldQuote quote : quotes) {
            quote.setRecommended(isRecommended(quote));
        }

        // Étape 2 : Trier les quotes
        // D'abord les recommandées (recommended = true) avant les non-recommandées
        // Ensuite par ordre de priorité interne (plus petit index = plus prioritaire)
        quotes.sort(Comparator
                .comparing((MeldQuote q) -> !Boolean.TRUE.equals(q.getRecommended())) // false (recommandé) avant true (non-recommandé)
                .thenComparing(this::getProviderPriority)
        );
    }

    // ==========================================
    // Méthodes privées : Logique de recommandation
    // ==========================================

    /**
     * Détermine si un provider est recommandé en fonction du pays et du montant.
     *
     * @param quote Le quote Meld à évaluer
     * @return true si le provider est recommandé, false sinon
     */
    private boolean isRecommended(MeldQuote quote) {
        if (quote == null || quote.getServiceProvider() == null) {
            return false;
        }

        String provider = quote.getServiceProvider();
        String countryCode = quote.getCountryCode();
        Double sourceAmount = quote.getSourceAmount();

        // Critère 1 : Pays africain → FONBNK recommandé
        if (countryCode != null && AFRICAN_COUNTRY_CODES.contains(countryCode.toUpperCase())) {
            return PROVIDERS_AFRICA_ORDERED.contains(provider);
        }

        // Critère 2 : Montant (si disponible)
        if (sourceAmount != null) {
            // Petits et moyens montants (10 à 14 000 EUR inclus)
            if (sourceAmount >= MINIMUM_AMOUNT && sourceAmount <= SMALL_MEDIUM_THRESHOLD) {
                return PROVIDERS_SMALL_MEDIUM_ORDERED.contains(provider);
            }
            // Gros montants (strictement supérieur à 14 000 jusqu'à 80 000 EUR)
            else if (sourceAmount > SMALL_MEDIUM_THRESHOLD && sourceAmount <= LARGE_THRESHOLD) {
                return PROVIDERS_LARGE_ORDERED.contains(provider);
            }
        }

        return false;
    }

    /**
     * Retourne l'index de priorité d'un provider dans les listes ordonnées.
     * Plus l'index est petit, plus le provider est prioritaire.
     *
     * @param quote Le quote Meld à évaluer
     * @return L'index de priorité (0 = le plus prioritaire, Integer.MAX_VALUE = non recommandé)
     */
    private int getProviderPriority(MeldQuote quote) {
        if (quote == null || quote.getServiceProvider() == null || !Boolean.TRUE.equals(quote.getRecommended())) {
            return Integer.MAX_VALUE;
        }

        String provider = quote.getServiceProvider();
        String countryCode = quote.getCountryCode();
        Double sourceAmount = quote.getSourceAmount();

        // Critère 1 : Pays africain
        if (countryCode != null && AFRICAN_COUNTRY_CODES.contains(countryCode.toUpperCase())) {
            int index = PROVIDERS_AFRICA_ORDERED.indexOf(provider);
            return index >= 0 ? index : Integer.MAX_VALUE;
        }

        // Critère 2 : Montant
        if (sourceAmount != null) {
            // Petits et moyens montants
            if (sourceAmount >= MINIMUM_AMOUNT && sourceAmount <= SMALL_MEDIUM_THRESHOLD) {
                int index = PROVIDERS_SMALL_MEDIUM_ORDERED.indexOf(provider);
                return index >= 0 ? index : Integer.MAX_VALUE;
            }
            // Gros montants
            else if (sourceAmount > SMALL_MEDIUM_THRESHOLD && sourceAmount <= LARGE_THRESHOLD) {
                int index = PROVIDERS_LARGE_ORDERED.indexOf(provider);
                return index >= 0 ? index : Integer.MAX_VALUE;
            }
        }

        return Integer.MAX_VALUE;
    }
}
