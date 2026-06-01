package org.akuunda.akuundawallet.backoffice.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataPointDto {
    private String label;
    private Double value;
    private Double value2;
    private String category;
}
