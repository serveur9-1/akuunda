package org.akuunda.akuundawallet.backoffice.exception;

import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.backoffice.dto.ApiError;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@Slf4j
@Order(1)
@RestControllerAdvice(basePackages = "org.akuunda.akuundawallet.backoffice.controller")
public class BackofficeExceptionHandler {

    @ExceptionHandler(BackofficeAuthException.class)
    public ResponseEntity<ApiError> handleBackofficeAuth(BackofficeAuthException ex) {
        log.warn("Backoffice auth: {}", ex.getMessage());
        ApiError err = new ApiError(
                "UNAUTHORIZED",
                ex.getMessage(),
                null,
                Instant.now().toString(),
                java.util.UUID.randomUUID().toString()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
    }

    @ExceptionHandler(BackofficeRequestException.class)
    public ResponseEntity<ApiError> handleBackofficeRequest(BackofficeRequestException ex) {
        log.warn("Backoffice request: {} — {}", ex.getCode(), ex.getMessage());
        ApiError err = new ApiError(
                ex.getCode(),
                ex.getMessage(),
                null,
                Instant.now().toString(),
                java.util.UUID.randomUUID().toString()
        );
        return ResponseEntity.status(ex.getStatus()).body(err);
    }

    /** Erreurs JAX-RS Keycloak non mappées ailleurs (évite un 500 générique GlobalExceptionHandler). */
    @ExceptionHandler(WebApplicationException.class)
    public ResponseEntity<ApiError> handleKeycloakJaxRs(WebApplicationException ex) {
        int st = ex.getResponse() != null ? ex.getResponse().getStatus() : 500;
        log.warn("Backoffice Keycloak JAX-RS: HTTP {}", st, ex);
        HttpStatus mapped = st >= 500 ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_REQUEST;
        String msg = ex.getMessage() != null ? ex.getMessage() : "Keycloak request failed";
        ApiError err = new ApiError(
                "KEYCLOAK_HTTP_" + st,
                msg,
                null,
                Instant.now().toString(),
                java.util.UUID.randomUUID().toString()
        );
        return ResponseEntity.status(mapped).body(err);
    }
}
