package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Marché Kyrrex")
public class KyrrexMarketResponse {

    @Schema(description = "Marché actif")
    private boolean active;

    @JsonProperty("available_quote_balance")
    @Schema(description = "Balance de quote disponible")
    private String availableQuoteBalance;

    @Schema(description = "Asset de base")
    private String baseAsset;

    @Schema(description = "Tag de l'asset de base")
    private String baseAssetTag;

    @Schema(description = "Précision de la base")
    private int basePrecision;

    @Schema(description = "Identifiant")
    private String id;

    @Schema(description = "Marché")
    private String market;

    @Schema(description = "Nom")
    private String name;

    @Schema(description = "Asset de quote")
    private String quoteAsset;

    @Schema(description = "Tag de l'asset de quote")
    private String quoteAssetTag;

    @Schema(description = "Précision de la quote")
    private int quotePrecision;
}
