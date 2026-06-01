package org.akuunda.akuundawallet.keycloak.impl.service;

import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.keycloak.api.dao.ReviewRequestRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.ReviewRequestDto;
import org.akuunda.akuundawallet.keycloak.api.entities.ReviewRequest;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.ReviewRequestService;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReviewRequestServiceImpl implements ReviewRequestService {

    private final ReviewRequestRepository reviewRequestRepository;

    private final UserRepository userRepository;

    @Override
    public void handleReviewRequest(final ReviewRequestDto reviewRequestDto) {

        //verify with user is in our data with userId (externalUserId)
        Optional<Users> userReview = userRepository.findById(reviewRequestDto.getExternalUserId());
        if (userReview.isPresent()) {
            ReviewRequest reviewRequest = new ReviewRequest();
            reviewRequest.setApplicantId(reviewRequestDto.getApplicantId());
            reviewRequest.setInspectionId(reviewRequestDto.getInspectionId());
            reviewRequest.setCorrelationId(reviewRequestDto.getCorrelationId());
            reviewRequest.setExternalUserId(reviewRequestDto.getExternalUserId());
            reviewRequest.setLevelName(reviewRequestDto.getLevelName());
            reviewRequest.setType(reviewRequestDto.getType());
            reviewRequest.setReviewResult(reviewRequestDto.getReviewResult());
            reviewRequest.setReviewStatus(reviewRequestDto.getReviewStatus());
            reviewRequest.setCreatedAtMs(reviewRequestDto.getCreatedAtMs());

            reviewRequestRepository.save(reviewRequest);

            //update user status with reviewAnswer (GREEN or RED)
            if (reviewRequest.getReviewResult().getReviewAnswer().equals("GREEN")) {
                //user verification is OK
                userReview.get().setIdentyVerify(true);
                userRepository.saveAndFlush(userReview.get());
            }
        }

    }
}
