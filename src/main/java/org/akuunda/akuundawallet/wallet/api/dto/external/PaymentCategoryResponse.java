package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCategoryResponse {
    private Long id;
    private String name;
    private String category;

}