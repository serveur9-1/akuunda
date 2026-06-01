package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.Data;

@Data
public class ErrorResponseDto {
    private String code;
    private String message;

    public ErrorResponseDto(String internalServerError, String uneErreurInterneEstSurvenue) {
    }
}
