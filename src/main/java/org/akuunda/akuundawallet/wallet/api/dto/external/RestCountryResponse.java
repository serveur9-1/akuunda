package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO pour mapper la réponse de l'API REST Countries (https://restcountries.com)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestCountryResponse {
    
    @JsonProperty("name")
    private CountryName name;
    
    @JsonProperty("cca2")
    private String countryCode; // Code ISO 2 lettres (ex: "CD", "CG")
    
    @JsonProperty("currencies")
    private Map<String, CurrencyInfo> currencies;
    
    @JsonProperty("idd")
    private PhoneCode idd;
    
    @JsonProperty("capital")
    private List<String> capital;
    
    @JsonProperty("region")
    private String region; // Ex: "Africa", "Europe"
    
    @JsonProperty("subregion")
    private String subregion;
    
    @JsonProperty("continents")
    private List<String> continents;
    
    @JsonProperty("flags")
    private Flags flags;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CountryName {
        @JsonProperty("common")
        private String common;
        
        @JsonProperty("official")
        private String official;
        
        @JsonProperty("nativeName")
        private Map<String, NativeName> nativeName;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NativeName {
        @JsonProperty("official")
        private String official;
        
        @JsonProperty("common")
        private String common;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrencyInfo {
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("symbol")
        private String symbol;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PhoneCode {
        @JsonProperty("root")
        private String root; // Ex: "+2"
        
        @JsonProperty("suffixes")
        private List<String> suffixes; // Ex: ["43"] pour +243
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Flags {
        @JsonProperty("png")
        private String png; // URL du drapeau PNG (ex: "https://flagcdn.com/w320/sn.png")
        
        @JsonProperty("svg")
        private String svg; // URL du drapeau SVG (ex: "https://flagcdn.com/sn.svg")
        
        @JsonProperty("alt")
        private String alt; // Description alternative du drapeau
    }
    
    /**
     * Extrait le code devise principal (premier dans la map)
     */
    public String getPrimaryCurrencyCode() {
        if (currencies == null || currencies.isEmpty()) {
            return null;
        }
        return currencies.keySet().iterator().next();
    }
    
    /**
     * Extrait l'indicatif téléphonique complet (root + suffixe)
     */
    public Integer getCallingCode() {
        if (idd == null || idd.root == null || idd.suffixes == null || idd.suffixes.isEmpty()) {
            return null;
        }
        try {
            String root = idd.root.replace("+", "");
            String suffix = idd.suffixes.get(0);
            return Integer.parseInt(root + suffix);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Extrait la capitale (première dans la liste)
     */
    public String getCapitalCity() {
        if (capital == null || capital.isEmpty()) {
            return null;
        }
        return capital.get(0);
    }
    
    /**
     * Extrait le nom du pays en français si disponible, sinon en anglais
     * Priorité : nativeName.fra.common > name.common > nativeName.fra.official > name.official
     */
    public String getCountryNameInFrench() {
        if (name == null) {
            return null;
        }
        
        // Essayer d'obtenir le nom commun en français depuis nativeName
        if (name.nativeName != null && name.nativeName.containsKey("fra")) {
            NativeName frenchName = name.nativeName.get("fra");
            if (frenchName != null && frenchName.common != null && !frenchName.common.isEmpty()) {
                return frenchName.common;
            }
            // Fallback sur le nom officiel en français si le commun n'est pas disponible
            if (frenchName != null && frenchName.official != null && !frenchName.official.isEmpty()) {
                return frenchName.official;
            }
        }
        
        // Sinon utiliser le nom commun en anglais (priorité sur official)
        if (name.common != null && !name.common.isEmpty()) {
            return name.common;
        }
        
        // Dernier fallback : nom officiel en anglais
        return name.official != null ? name.official : null;
    }
    
    /**
     * Extrait le continent (premier dans la liste)
     */
    public String getContinent() {
        if (continents != null && !continents.isEmpty()) {
            return continents.get(0);
        }
        // Fallback sur region si continents n'est pas disponible
        return region != null ? region : "Unknown";
    }
    
    /**
     * Extrait l'URL du drapeau PNG (priorité) ou SVG
     * @return URL du drapeau ou null si non disponible
     */
    public String getFlagUrl() {
        if (flags == null) {
            return null;
        }
        // Priorité au PNG (meilleure qualité pour l'affichage)
        if (flags.png != null && !flags.png.isEmpty()) {
            return flags.png;
        }
        // Fallback sur SVG si PNG n'est pas disponible
        if (flags.svg != null && !flags.svg.isEmpty()) {
            return flags.svg;
        }
        return null;
    }
}

