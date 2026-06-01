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
@Schema(description = "Réponse combinée create + pay")
public class CreateAndPayResponse {

    // --- From Create ---
    @Schema(description = "ID du lien")
    private Long id;

    @Schema(description = "Code unique du lien (8 caractères)")
    private String uniqueCode;

    @Schema(description = "Description du paiement")
    private String description;

    @Schema(description = "Statut du lien")
    private String status;

    @Schema(description = "Adresse CREATE2 du wallet temporaire")
    private String create2WalletAddress;

    @Schema(description = "Payment ID on-chain (bytes32)")
    private String paymentIdBytes32;

    @Schema(description = "Date d'expiration")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Date de création")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    // --- From Pay (Meld session) ---
    @Schema(description = "ID de la session Meld")
    private String meldSessionId;

    @Schema(description = "URL du widget Meld pour le paiement")
    private String meldWidgetUrl;

    // --- Payer info ---
    @Schema(description = "Numéro de téléphone du payeur")
    private String payerPhone;

    @Schema(description = "Nom du payeur")
    private String payerName;

    @Schema(description = "Email du payeur")
    private String payerEmail;
}
