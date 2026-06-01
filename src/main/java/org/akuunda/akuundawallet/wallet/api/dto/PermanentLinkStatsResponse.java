package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Statistiques d'un lien de paiement permanent")
public class PermanentLinkStatsResponse {

    @Schema(description = "Slug du marchand", example = "boutique-mama-coco")
    private String merchantSlug;

    @Schema(description = "Description du lien", example = "Paiement boutique")
    private String description;

    @Schema(description = "Lien actif ou non", example = "true")
    private Boolean isActive;

    @Schema(description = "Nombre total de sessions créées", example = "12")
    private Integer totalSessions;

    @Schema(description = "Nombre total de paiements complétés", example = "10")
    private Integer totalCompletedPayments;

    @Schema(description = "Montant total reçu", example = "50000.0")
    private Double totalAmountReceived;

    @Schema(description = "Nombre de sessions actives (CREATED ou PENDING)", example = "2")
    private Long activeSessions;

    @Schema(description = "Date de création du lien")
    private LocalDateTime createdAt;
}
