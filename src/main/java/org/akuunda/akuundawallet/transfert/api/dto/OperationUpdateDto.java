package org.akuunda.akuundawallet.transfert.api.dto;

import lombok.Data;

@Data
public class OperationUpdateDto {
    private String username;
    private String devise;
    private String status;
    private String type;
    private Double amount;
    private Double convertedAmount;
    private String operationHash;
}
