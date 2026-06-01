package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class ManageDataSafeguardRequestDto {

    private String msisdn;
    /** "active", "temporarily-inactive" or "permanently-inactive" */
    private String state;
    private String transactionReference;
    private String source;
}
