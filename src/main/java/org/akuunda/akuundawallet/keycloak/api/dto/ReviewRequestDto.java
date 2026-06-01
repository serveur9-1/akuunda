package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Data;
import lombok.ToString;
import org.akuunda.akuundawallet.keycloak.api.entities.ReviewResult;

@Data
@ToString
public class ReviewRequestDto {

    private String applicantId;
    private String inspectionId;
    private String correlationId;
    private String externalUserId;
    private String levelName;
    private String type;
    private ReviewResult reviewResult;
    private String reviewStatus;
    private String createdAtMs;
}
