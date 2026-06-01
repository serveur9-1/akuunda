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
@Schema(description = "Réponse web link KYC Kyrrex")
public class KyrrexKycWebLinkResponse {

    @Schema(description = "URL de vérification KYC Sumsub", example = "https://in.sumsub.com/websdk/p/r4oiITSnLIizvWPu")
    private String link;
}
