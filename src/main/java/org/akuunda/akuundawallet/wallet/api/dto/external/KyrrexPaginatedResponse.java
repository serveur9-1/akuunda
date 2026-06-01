package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Réponse paginée Kyrrex")
public class KyrrexPaginatedResponse<T> {

    @Schema(description = "Nombre total d'éléments")
    private Integer totalCount;

    @Schema(description = "Éléments par page")
    private Integer perPage;

    @Schema(description = "Nombre total de pages")
    private Integer totalPages;

    @Schema(description = "Page courante")
    private Integer page;

    @Schema(description = "Éléments de la page")
    private List<T> items;
}
