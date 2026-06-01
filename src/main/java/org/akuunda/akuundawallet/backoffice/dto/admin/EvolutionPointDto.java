package org.akuunda.akuundawallet.backoffice.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvolutionPointDto {
    private String mois;
    private long utilisateurs;
    private long transactions;
}
