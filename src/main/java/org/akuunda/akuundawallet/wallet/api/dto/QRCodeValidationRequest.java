package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour valider un QR code")
public class QRCodeValidationRequest {

    @NotBlank(message = "Le token du QR code est obligatoire")
    @Schema(
            description = "Token unique du QR code à valider",
            example = "qr-abc123def456"
    )
    private String qrCodeToken;

    @Schema(
            description = "Username de la personne qui scanne (client ou vendeur selon le contexte)",
            example = "0033612108828"
    )
    private String scannedBy;
}

