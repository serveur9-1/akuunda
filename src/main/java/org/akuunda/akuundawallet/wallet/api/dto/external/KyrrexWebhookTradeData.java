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
@Schema(description = "Données webhook trade Kyrrex")
public class KyrrexWebhookTradeData {

    private String createdAt;
    private String fee;
    private String feeAsset;
    private String funds;
    private String fundsAsset;
    private Long id;
    private String inAsset;
    private String market;
    private Long orderId;
    private String outAsset;
    private String price;
    private String side;
    private String type;
    private String volume;
    private String volumeAsset;
}
