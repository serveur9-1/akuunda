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
@Schema(description = "Données webhook KYC Kyrrex")
public class KyrrexWebhookKycData {

    @Schema(description = "Membre KYC vérifié")
    private KyrrexMember member;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Membre Kyrrex")
    public static class KyrrexMember {

        @Schema(description = "UID du membre")
        private String uid;

        @Schema(description = "Email du membre")
        private String email;

        @Schema(description = "Statut de vérification")
        private boolean verified;
    }
}
