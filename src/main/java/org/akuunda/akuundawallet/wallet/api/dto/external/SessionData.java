package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionData {

    private String clientIpAddress;  // Nouveau champ requis pour Unlimit
    private String walletAddress;
    private String countryCode;
    private String sourceCurrencyCode;
    private String sourceAmount;
    private String destinationCurrencyCode;
    private String serviceProvider;
    private String redirectUrl;
}
