package org.akuunda.akuundawallet.wallet.api.dto.partner;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BeneficiaryRequest {

    @NotBlank(message = "Le type de bénéficiaire est requis")
    /** VENDOR | PARTNER | OPERATOR | CUSTOM */
    private String beneficiaryType;

    @NotBlank(message = "Le libellé est requis")
    private String label;

    /** Adresse wallet Polygon (prioritaire sur username) */
    private String walletAddress;

    /** Username Akuunda Pay (utilisé si walletAddress absent) */
    private String username;

    @NotNull(message = "Le pourcentage est requis")
    @DecimalMin(value = "0.0001", message = "Le pourcentage doit être > 0")
    @DecimalMax(value = "1.0",    message = "Le pourcentage doit être <= 1.0 (100%)")
    private Double percentage;

    @Min(value = 1, message = "L'ordre d'exécution doit être >= 1")
    private Integer executionOrder = 1;
}
