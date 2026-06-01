package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.*;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@ToString
public class Result {

    private String id;
    private String address;
    private String walletType;
    private String secretType;
    private Date createdAt;
    private boolean archived;
    private String description;
    private boolean primary;
    private boolean hasCustomPin;
    private boolean custodial;
    private String userId;
    private Balance balance;
}
