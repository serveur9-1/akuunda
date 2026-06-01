package org.akuunda.akuundawallet.esim.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Page du catalogue Transatel (produits découpés côté serveur pour limiter la taille des réponses).
 */
@Data
@Schema(description = "Catalogue eSIM paginé")
public class CatalogPageDto {

    private String cos;
    private String currentLocale;
    private List<ProductDto> products;
    private List<LinkDto> links;

    @Schema(description = "Index du premier produit renvoyé (0-based)")
    private int offset;

    @Schema(description = "Nombre maximum de produits demandés pour cette page")
    private int limit;

    @Schema(description = "Nombre total de produits dans le catalogue Transatel")
    private int totalProducts;

    @Schema(description = "true s'il existe des produits après cette page")
    private boolean hasMore;

    @Schema(description = "Offset à passer dans la requête suivante pour la page suivante ; absent si hasMore est false")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer nextOffset;
}
