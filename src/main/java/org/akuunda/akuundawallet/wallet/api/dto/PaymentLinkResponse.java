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
@Schema(description = "Réponse contenant les informations d'un lien de paiement")
public class PaymentLinkResponse {

    @Schema(description = "ID du lien de paiement", example = "1")
    private Long id;

    @Schema(description = "Code unique du lien (à utiliser dans l'URL)", example = "gn1mb")
    private String uniqueCode;

    @Schema(description = "URL complète du lien de paiement", example = "https://akuunda-pay.io/pay/gn1mb")
    private String paymentUrl;

    @Schema(description = "Description/libellé du paiement", example = "Paiement facture électricité")
    private String description;

    @Schema(description = "Montant fixe (null si montant libre choisi par le payeur)", example = "5000.0")
    private Double amount;

    @Schema(description = "Code devise (null si devise choisie par le payeur)", example = "XOF")
    private String currency;

    @Schema(description = "Si le lien est actif", example = "true")
    private Boolean isActive;

    @Schema(description = "Date d'expiration (null si pas d'expiration)")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Nombre total de paiements reçus", example = "5")
    private Integer totalPayments;

    @Schema(description = "Montant total reçu", example = "25000.0")
    private Double totalAmountReceived;

    @Schema(description = "Username du créateur", example = "002250759146858")
    private String creatorUsername;

    @Schema(description = "Date de création")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Date de dernière mise à jour")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}

