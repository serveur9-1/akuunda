package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class CanSubscribeDto {

    private boolean allowed;
    private String mode;

    private String errorKey;
    private String errorMessage;
}
