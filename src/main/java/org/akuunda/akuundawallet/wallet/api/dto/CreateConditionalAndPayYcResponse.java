package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse combinée create + pay YellowCard pour lien conditionnel (escrow)")
public class CreateConditionalAndPayYcResponse {

    // --- From Create (conditional link) ---
    @Schema(description = "ID du lien")
    private Long id;

    @Schema(description = "Code unique du lien (8 caractères)")
    private String uniqueCode;

    @Schema(description = "Description du paiement")
    private String description;

    @Schema(description = "Statut du lien (PENDING ou CREATED si YC a échoué)")
    private String status;

    @Schema(description = "Adresse CREATE2 du wallet temporaire (escrow)")
    private String create2WalletAddress;

    @Schema(description = "Payment ID on-chain (bytes32)")
    private String paymentIdBytes32;

    @Schema(description = "Date d'expiration")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Date de création")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    // --- Payer info ---
    @Schema(description = "Numéro de téléphone du payeur")
    private String payerPhone;

    @Schema(description = "Nom du payeur")
    private String payerName;

    @Schema(description = "Email du payeur")
    private String payerEmail;

    // --- YellowCard payment fields ---
    @Schema(description = "ID de la transaction YellowCard (sequenceId)")
    private String yellowCardTransactionId;

    @Schema(description = "URL de redirection YellowCard pour paiement Wave/Orange Money (si disponible)")
    private String redirectUrl;

    @Schema(description = "Informations bancaires pour virement (si disponible)")
    private Map<String, Object> bankInfo;

    @Schema(description = "Méthode de paiement détectée: 'push' (MoMo), 'link' (Wave/Orange), 'bank' (virement)")
    private String paymentMethod;
}
