package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO simplifié pour les réponses d'historique de transactions.
 * Format compréhensible pour un utilisateur final.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Réponse simplifiée d'une transaction pour l'historique utilisateur")
public class SimpleTransactionResponse {
    
    @Schema(description = "Identifiant unique de la transaction (ID réel de l'opérateur : merchant_oid pour MT Pelerin, sequenceId pour YellowCard, externalTransactionId pour Guardarian)", 
            example = "0033612108828-27301bc3-7715-4966-b9a3-6aadb39e9dd8", required = true)
    @JsonProperty("id")
    private String id;
    
    @Schema(description = "Statut de la transaction (completed, pending, failed, etc.)", 
            example = "completed", required = true)
    @JsonProperty("status")
    private String status;
    
    @Schema(description = "Date de la transaction au format ISO 8601", 
            example = "2026-01-12T14:05:00Z", required = true)
    @JsonProperty("date")
    private Instant date;
    
    @Schema(description = "Montant de la transaction (avec décimales pour XOF/XAF, en unités pour EUR)", 
            example = "6557.49", required = true)
    @JsonProperty("amount")
    private Double amount;
    
    @Schema(description = "Devise de la transaction (XOF, EUR, etc.)", 
            example = "XOF", required = true)
    @JsonProperty("currency")
    private String currency;
    
    @Schema(description = "Opérateur de la transaction (MTPELERIN, YELLOWCARD, GUARDIARAN)", 
            example = "MTPELERIN", required = true)
    @JsonProperty("operator")
    private String operator;
    
    @Schema(description = "Type de transaction (ONRAMP = dépôt fiat vers crypto, OFFRAMP = retrait crypto vers fiat)", 
            example = "ONRAMP", required = true)
    @JsonProperty("type")
    private String type;
}
