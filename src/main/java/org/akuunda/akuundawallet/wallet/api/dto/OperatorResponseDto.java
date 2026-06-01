package org.akuunda.akuundawallet.wallet.api.dto;

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
@Schema(description = "Réponse pour un opérateur mobile money (filtré depuis les channels YellowCard)")
public class OperatorResponseDto {

    @Schema(description = "ID du channel YellowCard (à utiliser comme operatorId dans WebPaymentRequest)", 
            example = "37b63794-284b-4a09-863b-9b74a3f621e1")
    private String id;

    @Schema(description = "Nom de l'opérateur/méthode de paiement", 
            example = "Mobile Money")
    private String name;

    @Schema(description = "Type de canal", 
            example = "momo")
    private String channelType;

    @Schema(description = "Code pays ISO", 
            example = "CI")
    private String country;

    @Schema(description = "Code devise", 
            example = "XOF")
    private String currency;

    @Schema(description = "Type de ramp (INSTANT ou MANUAL)", 
            example = "INSTANT")
    private String rampType;

    @Schema(description = "Montant minimum", 
            example = "500.0")
    private Double minAmount;

    @Schema(description = "Montant maximum", 
            example = "250000.0")
    private Double maxAmount;

    @Schema(description = "Statut du canal", 
            example = "active")
    private String status;
}

