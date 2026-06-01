package org.akuunda.akuundawallet.backoffice.exception;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier backoffice (conflit, interdit, requête invalide, etc.).
 */
public class BackofficeRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BackofficeRequestException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
