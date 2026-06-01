package org.akuunda.akuundawallet.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration pour le mapping statique des channels YellowCard.
 * Résout le problème de channelId incorrect envoyé par le frontend.
 * 
 * Mapping: country -> rampType -> networkId -> channelId
 * Exemple: CI -> deposit -> 90d4d5ea-... -> 7c7e79fe-...
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "yellowcard")
public class YellowCardChannelConfig {
    
    /**
     * Static mapping: country -> rampType -> networkId -> channelId
     * Example: CI -> deposit -> 90d4d5ea-... -> 7c7e79fe-...
     */
    private Map<String, Map<String, Map<String, String>>> channelOverrides = new HashMap<>();
    
    /**
     * Resolves the correct channelId for a given country, rampType, and networkId.
     * Returns null if no override is configured.
     * 
     * @param countryCode The country code (e.g., "CI", "BJ")
     * @param rampType The ramp type ("deposit" or "withdraw")
     * @param networkId The network ID (e.g., "90d4d5ea-71a3-4490-8bb7-0f1e0dfa735b")
     * @return The resolved channelId, or null if no mapping exists
     */
    public String resolve(String countryCode, String rampType, String networkId) {
        if (countryCode == null || rampType == null || networkId == null) {
            return null;
        }
        Map<String, Map<String, String>> countryMap = channelOverrides.get(countryCode);
        if (countryMap == null) return null;
        
        Map<String, String> rampMap = countryMap.get(rampType);
        if (rampMap == null) return null;
        
        return rampMap.get(networkId);
    }
}
