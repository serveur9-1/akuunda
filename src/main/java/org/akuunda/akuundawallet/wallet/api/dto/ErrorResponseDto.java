package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponseDto {
    private int errorCode;
    private String details;

    public ErrorResponseDto(int errorCode) {
        this.errorCode = errorCode;
    }
}
