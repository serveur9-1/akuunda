package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class LineStatusResponseDto {

    private String userId;
    private String simSerial;
    private String msisdn;
    private String localStatus;
    private String subscriberStatus;
    private String planStatus;
    private boolean hasActivePlan;
    private String userStatus;
}
