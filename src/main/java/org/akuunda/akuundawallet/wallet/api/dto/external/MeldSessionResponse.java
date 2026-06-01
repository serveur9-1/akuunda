package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // <-- ignore les champs inconnus
public class MeldSessionResponse {

    private String id;
    private String externalSessionId;
    private String externalCustomerId;
    private String customerId;
    private String widgetUrl;

    private String serviceProviderWidgetUrl;

    private String token;

    // Short redirect URL returned to frontend instead of the raw provider URL
    private String payRedirectUrl;
}
