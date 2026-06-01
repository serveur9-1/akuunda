package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.akuunda.akuundawallet.wallet.api.dto.external.RecipientDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.SourceDto;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenereLinkRequest {

    private RecipientDto recipient;
    private SourceDto source;
    private Double amount;
    private String currency;
    private String country;
    private String reason;
    private String channelId;

    /** CREATE2 wallet address used as the YellowCard settlement wallet. */
    private String settlementWalletAddress;

    /** Unique code of the one-time payment link, used by generelinkYC to build the dynamic redirectUrl
     *  pointing to https://qr.akuunda-pay.io/confirmation/{uniqueCode} */
    private String uniqueCode;
}
