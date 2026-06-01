package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
@Schema(description = "Réponse contenant les informations d'un lien de paiement permanent")
public class PermanentLinkResponse {

    @Schema(description = "ID du lien", example = "1")
    private Long id;

    @Schema(description = "Slug unique du marchand", example = "boutique-mama-coco")
    private String merchantSlug;

    @Schema(description = "URL publique du lien de paiement", example = "https://qr.akuunda-pay.io/m/boutique-mama-coco")
    private String paymentUrl;

    @Schema(description = "Description du lien", example = "Paiement boutique")
    private String description;

    @Schema(description = "Montant fixe (null = montant libre)", example = "5000.0")
    private Double amount;

    @Schema(description = "Devise", example = "XOF")
    private String currency;

    @Schema(description = "Lien actif ou non", example = "true")
    private Boolean isActive;

    @Schema(description = "Nombre total de sessions créées", example = "12")
    private Integer totalSessions;

    @Schema(description = "Nombre total de paiements complétés", example = "10")
    private Integer totalCompletedPayments;

    @Schema(description = "Montant total reçu", example = "50000.0")
    private Double totalAmountReceived;

    @Schema(description = "Username du créateur (nom historique). Même valeur que merchantUsername.")
    private String creatorUsername;

    @Schema(description = "Username marchand Akuunda Pay : clé de rattachement chez nous (Meld externalCustomerId, résolution profil/wallet). Même valeur que creatorUsername.")
    private String merchantUsername;

    @Schema(description = "Identifiant technique marchand (Keycloak sub, users.user_id). CustomerUID YellowCard sur le flux lien permanent.")
    private String merchantUserId;

    @Schema(description = "Prénom du créateur", example = "David")
    private String creatorFirstname;

    @Schema(description = "Nom du créateur", example = "Kouassi")
    private String creatorLastname;

    @Schema(description = "Date de création")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Date de dernière mise à jour")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
