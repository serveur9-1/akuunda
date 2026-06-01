package org.akuunda.akuundawallet.backoffice.service;

import org.akuunda.akuundawallet.backoffice.entity.BackofficeUser;

public interface BackofficeTokenService {

    String createAccessToken(BackofficeUser user);

    int getExpiresInSeconds();
}
