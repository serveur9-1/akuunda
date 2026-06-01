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
@Schema(description = "Liste paginée des sessions Kyrrex")
public class KyrrexSessionListResponse {

    @JsonProperty("total_count")
    @Schema(description = "Nombre total de sessions")
    private Integer totalCount;

    @JsonProperty("per_page")
    @Schema(description = "Nombre d'éléments par page")
    private Integer perPage;

    @JsonProperty("total_pages")
    @Schema(description = "Nombre total de pages")
    private Integer totalPages;

    @Schema(description = "Page actuelle")
    private Integer page;

    @JsonProperty("prev_page")
    @Schema(description = "Page précédente")
    private Integer prevPage;

    @JsonProperty("next_page")
    @Schema(description = "Page suivante")
    private Integer nextPage;

    @Schema(description = "Liste des sessions")
    private List<KyrrexSessionItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KyrrexSessionItem {

        @JsonProperty("access_key")
        @Schema(description = "Clé d'accès de la session")
        private String accessKey;

        @JsonProperty("created_at")
        @Schema(description = "Date de création")
        private String createdAt;

        @JsonProperty("expire_at")
        @Schema(description = "Date d'expiration")
        private String expireAt;

        @JsonProperty("updated_at")
        @Schema(description = "Date de mise à jour")
        private String updatedAt;
    }
}
