package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Informations bancaires retournées par YellowCard pour un paiement par virement")
public class YcBankInfoDto {

    @Schema(description = "Numéro de compte bancaire")
    private String accountNumber;

    @Schema(description = "Nom du titulaire du compte")
    private String accountName;

    @Schema(description = "Nom de la banque")
    private String bankName;

    @Schema(description = "Lien de paiement (Wave, Orange Money, etc.)")
    private String paymentLink;

    @Schema(description = "Référence de la transaction à indiquer lors du virement")
    private String reference;
}
