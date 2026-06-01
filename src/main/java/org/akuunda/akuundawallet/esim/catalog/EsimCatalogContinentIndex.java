package org.akuunda.akuundawallet.esim.catalog;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Charge {@code /esim/catalog-continent-iso3.tsv} (alpha-3 ISO → grands ensembles géographiques).
 */
@Slf4j
public final class EsimCatalogContinentIndex {

    private static final Map<String, Set<String>> BUCKET_TO_ISO3 = load();

    private EsimCatalogContinentIndex() {
    }

    private static Map<String, Set<String>> load() {
        Map<String, Set<String>> map = new HashMap<>();
        InputStream in = EsimCatalogContinentIndex.class.getResourceAsStream("/esim/catalog-continent-iso3.tsv");
        if (in == null) {
            log.warn("Ressource absente: esim/catalog-continent-iso3.tsv — filtre continent désactivé.");
            return Map.of();
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                String iso3 = line.substring(0, tab).trim().toUpperCase(Locale.ROOT);
                String bucket = line.substring(tab + 1).trim().toUpperCase(Locale.ROOT);
                if (iso3.isEmpty() || bucket.isEmpty()) {
                    continue;
                }
                map.computeIfAbsent(bucket, k -> new HashSet<>()).add(iso3);
            }
        } catch (Exception e) {
            log.error("Impossible de charger catalog-continent-iso3.tsv: {}", e.getMessage());
            return Map.of();
        }
        Map<String, Set<String>> frozen = new HashMap<>();
        map.forEach((k, v) -> frozen.put(k, Collections.unmodifiableSet(v)));
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * @param bucketKey ex. AFRICA, EUROPE, MIDDLE_EAST, NORTH_AMERICA, AMERICAS…
     */
    public static boolean anyCountryInBucket(Collection<String> countryIso3List, String bucketKey) {
        if (bucketKey == null || bucketKey.isBlank()) {
            return true;
        }
        String key = bucketKey.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        Set<String> bucket = BUCKET_TO_ISO3.get(key);
        if (bucket == null || bucket.isEmpty()) {
            log.debug("Continent/région catalogue inconnu: {}", key);
            return false;
        }
        if (countryIso3List == null || countryIso3List.isEmpty()) {
            return false;
        }
        for (String c : countryIso3List) {
            if (c != null && bucket.contains(c.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

}
