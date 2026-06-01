package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Body de POST /v3/orders.
 *
 * Champs requis côté TransFi : purposeCode, orderType, source, destination.
 * userId est requis pour onramp, offramp, payin, payout, swap, gaming
 * (non requis pour fiat_prefund / crypto_prefund).
 *
 * orderType : onramp | offramp | payin | payout | fiat_prefund | crypto_prefund | swap | gaming
 *
 * Les URLs (success/failure/source) doivent contenir le protocole https://. Elles peuvent
 * être omises si un redirectUrl par défaut est configuré dans Displ-AI, sauf pour les
 * orderType=gaming et les flux non-headless onramp/offramp/payin/swap.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiCreateOrderRequest {
    /**
     * Username wallet Akuunda (téléphone). Si renseigné, le serveur complète destination (onramp/payin/gaming)
     * ou source (offramp/payout) avec l'adresse et la devise crypto du wallet lorsque ces champs sont absents.
     * Jamais envoyé à TransFi (WRITE_ONLY).
     */
    @Schema(description = "Username wallet Akuunda — enrichit source/destination depuis la base (non transmis à TransFi)",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;

    private String partnerId;
    private String userId;
    private String invoiceId;
    private String orderType;
    private String purposeCode;
    private String purposeCodeReason;
    private String sourceUrl;
    private String successRedirectUrl;
    private String failureRedirectUrl;
    private Boolean headlessMode;
    private Map<String, Object> customerMetaData;
    private Map<String, Object> customization;
    private Map<String, Object> deviceDetails;
    private TransfiOrderSourceDto source;
    private TransfiOrderDestinationDto destination;
}
