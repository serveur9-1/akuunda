package org.akuunda.akuundawallet.common.Exceptions;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.akuunda.akuundawallet.wallet.service.infrastructure.KyrrexCredentialMissingException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.error("❌ Missing request parameter: {}", ex.getMessage());

        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        errors.add(new ErrorResponse.Error("MISSING_PARAMETER", "Required parameter '" + ex.getParameterName() + "' is missing", ex.getParameterName()));

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrors(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.error("❌ Validation error: {}", ex.getMessage());
        
        List<ErrorResponse.Error> errors = new ArrayList<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            log.error("  - Field '{}': {}", fieldName, errorMessage);
            errors.add(new ErrorResponse.Error("VALIDATION_ERROR", errorMessage, fieldName));
        });
        
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrors(new ArrayList<>(errors));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.error("❌ JSON parsing error: {}", ex.getMessage());
        Throwable rootCause = ex.getRootCause();
        String rootCauseMessage = rootCause != null ? rootCause.getMessage() : "Unknown";
        log.error("❌ Root cause: {}", rootCauseMessage);
        
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        errors.add(new ErrorResponse.Error(
                "JSON_PARSE_ERROR", 
                "Invalid JSON format: " + rootCauseMessage,
                null
        ));
        
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrors(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        log.error("❌ Constraint violation: {}", ex.getMessage());
        
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            log.error("  - Field '{}': {}", fieldName, errorMessage);
            errors.add(new ErrorResponse.Error("CONSTRAINT_VIOLATION", errorMessage, fieldName));
        });
        
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrors(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(KyrrexCredentialMissingException.class)
    public ResponseEntity<ErrorResponse> handleKyrrexCredentialMissing(KyrrexCredentialMissingException ex) {
        log.warn("❌ Kyrrex credentials missing: {}", ex.getMessage());
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        String code = ex.isRevoked() ? "KYRREX_CREDENTIAL_REVOKED" : "KYRREX_CREDENTIAL_MISSING";
        errors.add(new ErrorResponse.Error(code, ex.getMessage(), "username"));
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrors(errors);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("❌ Response status exception: {} {}", ex.getStatusCode().value(), ex.getReason());
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        errors.add(new ErrorResponse.Error("HTTP_" + ex.getStatusCode().value(), reason, null));
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrors(errors);
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("❌ Unexpected error: {}", ex.getMessage(), ex);

        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        errors.add(new ErrorResponse.Error("INTERNAL_ERROR", "An unexpected error occurred: " + ex.getMessage(), null));

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setSuccess(false);
        errorResponse.setErrors(errors);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

