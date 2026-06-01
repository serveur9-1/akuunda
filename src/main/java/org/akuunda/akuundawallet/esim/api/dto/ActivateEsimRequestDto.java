package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActivateEsimRequestDto {

    private String simSerial;
    private String ratePlan;
    private String externalReference;
    private String group;
    private String subscriberCountryOfResidence;
    private List<EsimOptionDto> options;
}
