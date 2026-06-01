package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class MsisdnResponseDto {

    private String userId;
    private String simSerial;
    private String msisdn;
    private String status;
    /** IDs des souscriptions appartenant à cet utilisateur (filtre côté mobile). */
    private List<String> subscriptionIds;
}
