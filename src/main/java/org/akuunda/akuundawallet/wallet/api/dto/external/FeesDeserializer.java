package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class FeesDeserializer extends JsonDeserializer<Double> {
    
    @Override
    public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        // Si c'est un nombre, le retourner directement
        if (node.isNumber()) {
            return node.asDouble();
        }
        
        // Si c'est un objet, extraire networkFee et fixFee et les additionner
        if (node.isObject()) {
            double totalFees = 0.0;
            
            // Extraire networkFee (peut être une chaîne ou un nombre)
            if (node.has("networkFee")) {
                JsonNode networkFeeNode = node.get("networkFee");
                if (networkFeeNode.isTextual()) {
                    try {
                        totalFees += Double.parseDouble(networkFeeNode.asText());
                    } catch (NumberFormatException e) {
                        // Ignorer si ce n'est pas un nombre valide
                    }
                } else if (networkFeeNode.isNumber()) {
                    totalFees += networkFeeNode.asDouble();
                }
            }
            
            // Extraire fixFee (peut être un nombre)
            if (node.has("fixFee")) {
                JsonNode fixFeeNode = node.get("fixFee");
                if (fixFeeNode.isNumber()) {
                    totalFees += fixFeeNode.asDouble();
                } else if (fixFeeNode.isTextual()) {
                    try {
                        totalFees += Double.parseDouble(fixFeeNode.asText());
                    } catch (NumberFormatException e) {
                        // Ignorer si ce n'est pas un nombre valide
                    }
                }
            }
            
            // Si aucun frais trouvé, essayer d'autres champs possibles
            if (totalFees == 0.0) {
                if (node.has("amount")) {
                    return node.get("amount").asDouble();
                }
                if (node.has("value")) {
                    return node.get("value").asDouble();
                }
                if (node.has("total")) {
                    return node.get("total").asDouble();
                }
            }
            
            return totalFees > 0.0 ? totalFees : null;
        }
        
        // Si c'est une chaîne, essayer de la parser
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }
}

