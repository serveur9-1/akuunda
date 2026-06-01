package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeldQuoteRequest {
    @JsonProperty("username")
    private String username;

    @JsonProperty("sourceCurrencyCode")
    private String sourceCurrencyCode;

    @JsonProperty("destinationCurrencyCode")
    private String destinationCurrencyCode;

    @JsonProperty("sourceAmount")
    private Double sourceAmount;

    @JsonProperty("countryCode")
    private String countryCode;

    @JsonProperty("walletAddress")
    private String walletAddress;

    /** Filtre sur un provider spécifique (ex. "MERCURYO"). Null = tous les providers. */
    @JsonProperty("serviceProviders")
    private java.util.List<String> serviceProviders;
}
