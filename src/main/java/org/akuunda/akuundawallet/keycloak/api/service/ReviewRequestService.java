package org.akuunda.akuundawallet.keycloak.api.service;

import org.akuunda.akuundawallet.keycloak.api.dto.ReviewRequestDto;
import org.springframework.validation.annotation.Validated;

@Validated
public interface ReviewRequestService {

    /**
     * {@inheritDoc}
     */
    void handleReviewRequest(final ReviewRequestDto reviewRequestDto);
}
