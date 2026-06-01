package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AmountDto {

    private Integer numberAmount;
    private Double amount;
}
