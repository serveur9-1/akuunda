package org.akuunda.akuundawallet.backoffice.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoTransactionDto {
    private String pays;
    private long envois;
    private long receptions;
}
