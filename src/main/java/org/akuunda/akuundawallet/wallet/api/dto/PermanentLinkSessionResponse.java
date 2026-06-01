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
@Schema(description = "Réponse contenant les informations d'une session de paiement permanent")
public class PermanentLinkSessionResponse {

    @Schema(description = "ID de la session", example = "1")
    private Long id;

    @Schema(description = "Code unique de la session (12 caractères)", example = "a1b2c3d4e5f6")
    private String sessionCode;

    @Schema(description = "Slug du lien permanent associé", example = "boutique-mama-coco")
    private String permanentLinkSlug;

    @Schema(description = "Montant de cette session", example = "25.0")
    private Double amount;

    @Schema(description = "Devise", example = "USDC")
    private String currency;

    @Schema(description = "Référence client", example = "CMD-01")
    private String reference;

    @Schema(description = "Statut de la session (CREATED, PENDING, PAID, SWEPT, EXPIRED, FAILED, CANCELLED)", example = "CREATED")
    private String status;

    @Schema(description = "Adresse CREATE2 du wallet temporaire")
    private String create2WalletAddress;

    @Schema(description = "Payment ID on-chain (bytes32)")
    private String paymentIdBytes32;

    @Schema(description = "Nom du payeur", example = "Jean Dupont")
    private String payerName;

    @Schema(description = "Téléphone du payeur", example = "+2250700123456")
    private String payerPhone;

    @Schema(description = "Email du payeur", example = "jean.dupont@example.com")
    private String payerEmail;

    @Schema(description = "Date d'expiration de la session")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "ID de transaction externe (YellowCard ou Meld)")
    private String externalTransactionId;

    @Schema(description = "Provider de paiement utilisé (YELLOWCARD ou MELD)")
    private String paymentProvider;

    @Schema(description = "Code pays du payeur", example = "CI")
    private String countryCode;

    @Schema(description = "Username marchand Akuunda Pay (créateur du lien). Pour Meld : aligné sur externalCustomerId côté serveur ; à afficher / pré-remplir côté client.")
    private String merchantUsername;

    @Schema(description = "Identifiant technique marchand (users.user_id / Keycloak sub). CustomerUID YellowCard pour cette session.")
    private String merchantUserId;

    @Schema(description = "Date de paiement")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paidAt;

    @Schema(description = "Date de création")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
