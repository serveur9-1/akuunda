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
@Schema(description = "Réponse de création de checkout")
public class CheckoutResponse {

    @Schema(description = "URL de la page de checkout hébergée", example = "https://qr.akuunda-pay.io/checkout/ch_a1b2c3d4")
    private String checkoutUrl;

    @Schema(description = "Code unique de la session de checkout", example = "ch_a1b2c3d4e5f6")
    private String checkoutCode;

    @Schema(description = "Date d'expiration du checkout")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expiresAt;

    @Schema(description = "Référence de la commande", example = "CMD-123")
    private String reference;

    @Schema(description = "Montant", example = "15000.0")
    private Double amount;

    @Schema(description = "Devise", example = "XOF")
    private String currency;
}
