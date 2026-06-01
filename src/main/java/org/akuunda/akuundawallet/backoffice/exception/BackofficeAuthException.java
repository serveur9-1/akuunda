package org.akuunda.akuundawallet.backoffice.exception;

/**
 * Exception d'authentification backoffice (login invalide, non authentifié).
 */
public class BackofficeAuthException extends RuntimeException {
    public BackofficeAuthException(String message) {
        super(message);
    }
}
