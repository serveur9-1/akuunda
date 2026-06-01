package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Asset disponible sur Kyrrex avec ses chaînes")
public class KyrrexAssetResponse {

    @Schema(description = "Identifiant de l'asset", example = "btc")
    private String asset;

    @Schema(description = "Liste des chaînes disponibles pour cet asset")
    private List<KyrrexAssetDchainResponse> dchains;

    @Schema(description = "Nom de l'asset", example = "Bitcoin")
    private String name;

    @Schema(description = "Précision décimale", example = "8")
    private Integer precision;

    @Schema(description = "Tag/memo")
    private String tag;

    @Schema(description = "Type d'asset", example = "crypto")
    private String type;
}
