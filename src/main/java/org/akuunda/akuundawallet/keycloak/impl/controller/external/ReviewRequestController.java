package org.akuunda.akuundawallet.keycloak.impl.controller.external;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dto.ReviewRequestDto;
import org.akuunda.akuundawallet.keycloak.api.service.ReviewRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RequestMapping(path = "/api/public/v1/review", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@RestController
@CrossOrigin("*")
@Tag(name = "Akunnda - Review - Identity")
public class ReviewRequestController {

    private final ReviewRequestService reviewRequestService;

    @PostMapping("/applicantReviewed")
    public ResponseEntity<String> handleErrorReview(@RequestBody ReviewRequestDto reviewRequestDto) {
        log.info("Review controller - applicantReviewed with reviewRequestDto {} ", reviewRequestDto);
        log.debug("Review controller - applicantReviewed with reviewRequestDto {} ", reviewRequestDto);
        reviewRequestService.handleReviewRequest(reviewRequestDto);
        return new ResponseEntity<>("Received with Success applicantReviewed processed", HttpStatus.OK);
    }
}
