package org.akuunda.akuundawallet.backoffice.service;

import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeLoginRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeLoginResponse;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeMeResponse;

/**
 * Service d'authentification pour le backoffice (login, me, refresh).
 */
public interface BackofficeAuthService {

    /**
     * Login backoffice : email/password + portal → token + user info.
     */
    BackofficeLoginResponse login(BackofficeLoginRequest request);

    /**
     * Infos de l'utilisateur courant (à partir du JWT).
     */
    BackofficeMeResponse me();
}
