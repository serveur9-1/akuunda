package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class AllowanceDataDto {

    private String resourceName;
    private String resourceUnit;
    private long resourceValue;

    private String resourceDurationUnit;
    private Integer resourceDuration;
}

