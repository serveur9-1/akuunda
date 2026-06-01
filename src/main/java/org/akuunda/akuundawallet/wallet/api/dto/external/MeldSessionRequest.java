package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeldSessionRequest {

    private SessionData sessionData;
    private String sessionType;
    private String externalCustomerId;
    private String externalSessionId;
}
