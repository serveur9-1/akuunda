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
@Schema(description = "Données webhook dépôt Kyrrex")
public class KyrrexWebhookDepositData {

    private String address;
    private String amount;
    private String asset;
    private String assetType;
    private Object control;
    private String createdAt;
    private String dchain;
    private String doneAt;
    private String fee;
    private Object highRisk;
    private String status;
    private Object tag;
    private String txId;
    private String txLink;
    private String type;
    private String updatedAt;
    private String uid;
}
