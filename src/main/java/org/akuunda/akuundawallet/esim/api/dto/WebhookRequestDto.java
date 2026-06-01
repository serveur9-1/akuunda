package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class WebhookRequestDto {

    private String mvnoRef;
    private String status;
    private String targetUrl;
    private String email;
    private List<String> events;
}
