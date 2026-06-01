package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Niveau KYC Kyrrex")
public class KyrrexKycLevelsResponse {

    @Schema(description = "Numéro du niveau KYC")
    private int level;

    @Schema(description = "Statut du niveau KYC (ex: approved, pending, rejected)")
    private String status;
}
