package org.akuunda.akuundawallet.transfert.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionRequest {

        private String type;
        private String secretType;
        private String walletId;
        private String to;
        private String tokenId;
        private double value;
}
