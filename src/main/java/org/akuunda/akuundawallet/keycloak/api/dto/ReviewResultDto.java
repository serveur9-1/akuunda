package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReviewResultDto {
    private String moderationComment;
    private String clientComment;
    private String reviewAnswer;
    private List<String> rejectLabels;
    private String reviewRejectType;
    private List<String> buttonIds;

}
