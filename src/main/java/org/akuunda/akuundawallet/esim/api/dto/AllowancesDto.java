package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class AllowancesDto {

    private List<AllowanceDataDto> data;
}

