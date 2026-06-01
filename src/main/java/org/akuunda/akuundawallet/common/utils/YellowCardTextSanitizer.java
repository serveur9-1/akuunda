package org.akuunda.akuundawallet.common.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Normalise les champs texte envoyés à YellowCard (noms, motifs, etc.).
 * <p>
 * YellowCard exige pour les noms un prénom et un nom, chacun 2–50 caractères,
 * composés uniquement de lettres, espaces, points, apostrophes ou tirets.
 * Les virgules sont exclues des noms (ex: "N'Guessan, Anne" → "N'Guessan Anne").
 */
public final class YellowCardTextSanitizer {

    // Noms : lettres Unicode, espaces, points, apostrophe ASCII, tirets — PAS de virgule
    private static final Pattern DISALLOWED_NAME = Pattern.compile("[^\\p{L}\\s.'\\-]");
    // Texte libre : idem + virgule autorisée
    private static final Pattern DISALLOWED_FREE  = Pattern.compile("[^\\p{L}\\s,.'\\-]");
    // ? entre deux lettres = apostrophe corrompu (encodage Latin-1/Windows-1252)
    private static final Pattern APOSTROPHE_PLACEHOLDER = Pattern.compile("(?<=\\p{L})\\?(?=\\p{L})");
    private static final int MAX_PART_LENGTH = 50;

    private YellowCardTextSanitizer() {
    }

    /**
     * Prénom + nom (recipient.name, sender.name, accountName).
     * Supprime les virgules, corrige les apostrophes, garantit prénom + nom valides.
     */
    public static String sanitizePersonName(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = fixEncodingArtifacts(raw.trim());
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        s = DISALLOWED_NAME.matcher(s).replaceAll("");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isBlank()) {
            return s;
        }
        return ensureFirstAndLastName(s);
    }

    /**
     * Texte libre (reason, businessName, etc.) — pas de contrainte prénom/nom.
     */
    public static String sanitizeFreeText(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = fixEncodingArtifacts(raw.trim());
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        s = DISALLOWED_FREE.matcher(s).replaceAll("");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.matches("(?i)d[\\p{L}]p[\\p{L}]t\\s+de\\s+fonds?")) {
            return "Depot de fonds";
        }
        return s;
    }

    /**
     * Corrige les artefacts d'encodage :
     * - Apostrophes Unicode typographiques → apostrophe ASCII ' (U+0027)
     * - ? entre deux lettres = apostrophe corrompu → '
     * - U+FFFD et ? isolés → supprimés
     */
    private static String fixEncodingArtifacts(String s) {
        // Apostrophes typographiques Unicode → ASCII '
        s = s.replace('’', '\'')  // RIGHT SINGLE QUOTATION MARK '
             .replace('‘', '\'')  // LEFT SINGLE QUOTATION MARK '
             .replace('ʼ', '\'')  // MODIFIER LETTER APOSTROPHE
             .replace('ʹ', '\'')  // MODIFIER LETTER PRIME
             .replace('`', '\'')       // GRAVE ACCENT
             .replace('´', '\''); // ACUTE ACCENT ´
        // ? entre deux lettres = apostrophe corrompu (Latin-1 / Windows-1252)
        s = APOSTROPHE_PLACEHOLDER.matcher(s).replaceAll("'");
        // Supprimer les caractères de remplacement restants
        s = s.replace("�", "").replace("?", "");
        return s;
    }

    /**
     * Premier token = prénom, le reste = nom. Fusionne les tokens < 2 caractères avec le voisin.
     */
    private static String ensureFirstAndLastName(String name) {
        List<String> parts = new ArrayList<>(List.of(name.split("\\s+")));
        mergeShortTokens(parts);

        if (parts.isEmpty()) {
            return name;
        }
        if (parts.size() == 1) {
            return truncatePart(parts.get(0));
        }

        String firstName = truncatePart(parts.get(0));
        String lastName = truncatePart(String.join(" ", parts.subList(1, parts.size())));

        if (firstName.length() < 2 || lastName.length() < 2) {
            return truncatePart(name);
        }
        return firstName + " " + lastName;
    }

    private static void mergeShortTokens(List<String> parts) {
        boolean changed = true;
        while (changed && parts.size() > 1) {
            changed = false;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).length() >= 2) {
                    continue;
                }
                if (i > 0) {
                    parts.set(i - 1, parts.get(i - 1) + " " + parts.get(i));
                    parts.remove(i);
                } else if (parts.size() > 1) {
                    parts.set(0, parts.get(0) + " " + parts.get(1));
                    parts.remove(1);
                }
                changed = true;
                break;
            }
        }
    }

    private static String truncatePart(String part) {
        if (part == null) {
            return "";
        }
        String trimmed = part.trim();
        if (trimmed.length() <= MAX_PART_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_PART_LENGTH).trim();
    }
}
