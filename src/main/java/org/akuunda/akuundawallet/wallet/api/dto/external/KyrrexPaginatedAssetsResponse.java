package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Réponse paginée des assets Kyrrex")
public class KyrrexPaginatedAssetsResponse {

    @JsonProperty("total_count")
    @Schema(description = "Nombre total d'assets")
    private Integer totalCount;

    @JsonProperty("per_page")
    @Schema(description = "Nombre d'éléments par page")
    private Integer perPage;

    @JsonProperty("total_pages")
    @Schema(description = "Nombre total de pages")
    private Integer totalPages;

    @Schema(description = "Page courante")
    private Integer page;

    @JsonProperty("prev_page")
    @Schema(description = "Page précédente (null si première page)")
    private Integer prevPage;

    @JsonProperty("next_page")
    @Schema(description = "Page suivante (null si dernière page)")
    private Integer nextPage;

    @Schema(description = "Liste des assets")
    private List<KyrrexAssetResponse> items;
}
