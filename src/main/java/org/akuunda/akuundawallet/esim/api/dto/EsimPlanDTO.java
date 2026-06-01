package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class EsimPlanDTO {
private String id;
    private String name;
    private String dataVolume; // ex: "5GB"
    private double price;      // ex: 15.0
    private String currency;   // ex: "USDC"
    private int validityDays;  // ex: 30
}
