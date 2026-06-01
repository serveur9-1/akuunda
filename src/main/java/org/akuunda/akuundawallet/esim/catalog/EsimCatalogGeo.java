package org.akuunda.akuundawallet.esim.catalog;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Filtres « monde » et « région » basés sur les conventions d’identifiants produits Transatel.
 */
public final class EsimCatalogGeo {

    private static final Pattern WORLD_ID = Pattern.compile(
            "(?i)^(WW_|IOT_)|_(WORLD|GLOBAL)(_|$)|_INTERNATIONAL_|\\bWORLD\\b");

    private static final Pattern R_AFRICA = Pattern.compile("(?i)_AFRICA_|STACK_[A-Z0-9_]*AFRICA");
    private static final Pattern R_EUROPE = Pattern.compile("(?i)_EUROPE_|STACK_[A-Z0-9_]*EUROPE");
    private static final Pattern R_ASIA = Pattern.compile("(?i)_ASIA_|STACK_[A-Z0-9_]*ASIA");
    private static final Pattern R_OCEANIA = Pattern.compile("(?i)_OCEANIA_|_ANZ_|AUSTRALASIA");
    private static final Pattern R_NORTH_AM = Pattern.compile("(?i)_NORTHAMERICA_|_NORTH_AMERICA_");
    private static final Pattern R_SOUTH_AM = Pattern.compile("(?i)_SOUTHAMERICA_|_SOUTH_AMERICA_|_LATAM_");
    private static final Pattern R_MIDDLE_EAST = Pattern.compile("(?i)_MIDDLE_EAST_|_MENA_|_GCC_");
    private static final Pattern R_CARIBBEAN = Pattern.compile("(?i)_CARIBBEAN_|_CARIBE_");

    private EsimCatalogGeo() {
    }

    public static boolean isWorldwideProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return false;
        }
        return WORLD_ID.matcher(productId).find();
    }

    /**
     * Correspondance sur l’identifiant produit (bundles régionaux type MWC_STACK_*_AFRICA_*).
     *
     * @param regionToken AFRICA, EUROPE, ASIA, OCEANIA, ANZ, NORTH_AMERICA, SOUTH_AMERICA, LATAM, MIDDLE_EAST, CARIBBEAN
     */
    public static boolean matchesRegionBundle(String productId, String regionToken) {
        if (productId == null || regionToken == null || regionToken.isBlank()) {
            return false;
        }
        String t = regionToken.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        Pattern p = switch (t) {
            case "AFRICA" -> R_AFRICA;
            case "EUROPE" -> R_EUROPE;
            case "ASIA" -> R_ASIA;
            case "OCEANIA", "ANZ" -> R_OCEANIA;
            case "NORTH_AMERICA", "NORTHAMERICA" -> R_NORTH_AM;
            case "SOUTH_AMERICA", "SOUTHAMERICA", "LATAM" -> R_SOUTH_AM;
            case "MIDDLE_EAST", "MENA", "GCC" -> R_MIDDLE_EAST;
            case "CARIBBEAN" -> R_CARIBBEAN;
            default -> null;
        };
        if (p == null) {
            return productId.toUpperCase(Locale.ROOT).contains(t);
        }
        return p.matcher(productId).find();
    }
}
