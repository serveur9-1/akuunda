package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class ValidityPeriodDto {

    private String validityDurationUnit;
    private Integer validityDuration;
    private Integer maxOccurrences;
}

