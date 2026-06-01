package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Détails d'une chaîne (dchain) pour un asset Kyrrex")
public class KyrrexAssetDchainResponse {

    @JsonProperty("active_deposit")
    @Schema(description = "Dépôt actif sur cette chaîne")
    private Boolean activeDeposit;

    @JsonProperty("active_withdrawal")
    @Schema(description = "Retrait actif sur cette chaîne")
    private Boolean activeWithdrawal;

    @JsonProperty("aml_active")
    @Schema(description = "AML actif")
    private Boolean amlActive;

    @Schema(description = "Asset")
    private String asset;

    @Schema(description = "Chaîne")
    private String chain;

    @JsonProperty("confirmations_deposit")
    @Schema(description = "Nombre de confirmations pour dépôt")
    private Integer confirmationsDeposit;

    @JsonProperty("confirmations_withdrawal")
    @Schema(description = "Nombre de confirmations pour retrait")
    private Integer confirmationsWithdrawal;

    @JsonProperty("contact_aml")
    @Schema(description = "Contact AML")
    private String contactAml;

    @JsonProperty("contract_address")
    @Schema(description = "Adresse du contrat")
    private String contractAddress;

    @Schema(description = "Identifiant de la chaîne de dépôt")
    private String dchain;

    @Schema(description = "Nombre de décimales")
    private Integer digit;

    @JsonProperty("display_name")
    @Schema(description = "Nom d'affichage", example = "Bitcoin")
    private String displayName;

    @Schema(description = "Type de chaîne", example = "crypto")
    private String dtype;

    @JsonProperty("min_deposit")
    @Schema(description = "Montant minimum de dépôt", example = "0.0005")
    private String minDeposit;

    @JsonProperty("min_withdrawal")
    @Schema(description = "Montant minimum de retrait", example = "0.0005")
    private String minWithdrawal;

    @Schema(description = "Tag/memo")
    private String tag;

    @JsonProperty("tag_require")
    @Schema(description = "Tag requis")
    private Boolean tagRequire;

    @JsonProperty("tag_visible")
    @Schema(description = "Tag visible")
    private Boolean tagVisible;
}
