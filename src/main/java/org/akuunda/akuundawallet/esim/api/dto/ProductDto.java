package org.akuunda.akuundawallet.esim.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDto {

    private DisplayDto display;
    private CanSubscribeDto canSubscribe;
    private boolean hasSubProducts;
    private boolean inventoryActive;
    private AvailabilityDto availability;
    private PricesDto prices;
    private ProductDefinitionDto productDefinition;
    private List<LinkDto> links;

    @Schema(description = "Prix retail recommandé HT (ex. grille Excel SPC « Recommended Retail Price (excl. VAT) »)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal recommendedRetailPriceExclVat;

    @Schema(description = "Devise du prix retail recommandé (souvent EUR)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String recommendedRetailPriceCurrency;
}
