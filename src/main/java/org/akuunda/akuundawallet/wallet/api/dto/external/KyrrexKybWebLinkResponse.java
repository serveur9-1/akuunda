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
@Schema(description = "Réponse web link KYB Kyrrex")
public class KyrrexKybWebLinkResponse {

    @Schema(description = "URL de vérification KYB Sumsub", example = "https://in.sumsub.com/websdk/p/...")
    private String link;
}
